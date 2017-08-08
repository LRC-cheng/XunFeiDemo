package com.voice;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.permission.PermissionActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class MainActivity extends PermissionActivity {

    private static final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private static final String GRAMMAR_TYPE_ABNF = "abnf";
    private static final String GRAMMAR_TYPE_BNF = "bnf";

    //显示听写结果
    private TextView textView;

    //语音听写对象
    private SpeechRecognizer speechRecognizer;

    //语音听写UI
    private RecognizerDialog recognizerDialog;

    //是否显示听写UI
    private boolean isShowDialog = true;

    //缓存
    private SharedPreferences sharedPreferences;

    //用hashmap存储听写结果
    private HashMap<String, String> hashMap = new LinkedHashMap<String, String>();

    //引擎类型（云端或本地）
    private String mEngineType = null;

    //函数返回值
    private int ret = 0;
    // 语法、词典临时变量
    String mContent;

    // 云端语法文件
    private String mCloudGrammar = null;

    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initDate();

    }

    private void initDate() {
        speechRecognizer = SpeechRecognizer.createRecognizer(this, initListener);
        recognizerDialog = new RecognizerDialog(this, initListener);
        sharedPreferences = getSharedPreferences(this.getPackageName(), Context.MODE_PRIVATE);
        toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        //这里我直接将引擎类型设置为云端，因为本地需要下载讯飞语记，这里为了方便直接使用云端
        //有需要的朋友可以加个单选框 让用户选择云端或本地
        mEngineType = SpeechConstant.TYPE_CLOUD;

        mCloudGrammar = FucUtil.readFile(this,"grammar_sample.abnf","utf-8");
        mContent = new String(mCloudGrammar);
        //指定引擎类型
        recognizerDialog.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        recognizerDialog.setParameter(SpeechConstant.TEXT_ENCODING,"utf-8");
        ret = speechRecognizer.buildGrammar(GRAMMAR_TYPE_ABNF, mContent, mCloudGrammarListener);
    }

    private void initView() {
        textView = (TextView) findViewById(R.id.tv);
    }

    //开始听写
    public void start(View view) {

        checkPermission(new CheckPermListener() {
            @Override
            public void superPermission() {

                textView.setText("");
                hashMap.clear();
                setParams();

                if (isShowDialog) {
                    recognizerDialog.setListener(dialogListener);
                    recognizerDialog.show();
                } else {
                    ret = speechRecognizer.startListening(recognizerListener);
                    if (ret != ErrorCode.SUCCESS) {
                        Log.e("tag", "听写失败,错误码" + ret);
                    }
                }
            }
        }, R.string.record, PERMISSIONS_STORAGE);


    }

    //结束听写
    public void stop(View view) {
        Toast.makeText(this, "停止听写", Toast.LENGTH_SHORT).show();
        if (isShowDialog) {
            recognizerDialog.dismiss();
        } else {
            speechRecognizer.stopListening();
        }
    }

    //初始化监听器
    private InitListener initListener = new InitListener() {
        @Override
        public void onInit(int i) {
            if (i != ErrorCode.SUCCESS) {
                Log.e("tag", "初始化失败，错误码" + i);
            }
        }
    };


    /**
     * 云端构建语法监听器。
     */
    private GrammarListener mCloudGrammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if(error == null){
                String grammarID = new String(grammarId);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                if(!TextUtils.isEmpty(grammarId))
                    editor.putString(KEY_GRAMMAR_ABNF_ID, grammarID);
                editor.commit();
                Toast.makeText(MainActivity.this, "语法构建成功：" + grammarId, Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(MainActivity.this, "语法构建失败,错误码：" + error.getErrorCode(), Toast.LENGTH_SHORT).show();
            }
        }
    };

    //无UI监听器
    private RecognizerListener recognizerListener = new RecognizerListener() {
        @Override
        public void onVolumeChanged(final int i, byte[] bytes) {
            Log.e("tag", "返回数据大小" + bytes.length);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "当前音量" + i, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onBeginOfSpeech() {
            Log.e("tag", "开始说话");
        }

        @Override
        public void onEndOfSpeech() {
            Log.e("tag", "结束说话");
        }

        @Override
        public void onResult(RecognizerResult recognizerResult, boolean b) {
            if (recognizerResult != null) {
                Log.e("tag", "听写结果：" + recognizerResult.getResultString());
                printResult(recognizerResult);

            }

        }

        @Override
        public void onError(SpeechError speechError) {
            Log.e("tag", "错误信息" + speechError.getPlainDescription(true));

        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {
        }
    };

    //有UI监听器
    private RecognizerDialogListener dialogListener = new RecognizerDialogListener() {
        @Override
        public void onResult(RecognizerResult recognizerResult, boolean b) {
            if (recognizerResult != null) {
                Log.e("tag", "听写结果：" + recognizerResult.getResultString());
                printResult(recognizerResult);

            }
        }

        @Override
        public void onError(SpeechError speechError) {
            Log.e("tag", speechError.getPlainDescription(true));

        }
    };

    //输出结果，将返回的json字段解析并在textVie中显示
    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());

        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        hashMap.put(sn, text);

        StringBuffer resultBuffer = new StringBuffer();
        for (String key : hashMap.keySet()) {
            resultBuffer.append(hashMap.get(key));
        }

        textView.setText(resultBuffer.toString());
    }

    private void setParams() {
        //清空参数
        speechRecognizer.setParameter(SpeechConstant.PARAMS, null);
        //设置引擎
        speechRecognizer.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);

        String grammarId = sharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
        speechRecognizer.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);

        speechRecognizer.setParameter(SpeechConstant.SUBJECT,"asr");
        //设置返回数据类型
        speechRecognizer.setParameter(SpeechConstant.RESULT_TYPE, "json");
        //设置中文 普通话
        speechRecognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        speechRecognizer.setParameter(SpeechConstant.ACCENT, "mandarin");

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        speechRecognizer.setParameter(SpeechConstant.VAD_BOS,
                sharedPreferences.getString("iat_vadbos_preference", "4000"));

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        speechRecognizer.setParameter(SpeechConstant.VAD_EOS,
                sharedPreferences.getString("iat_vadeos_preference", "1000"));

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        speechRecognizer.setParameter(SpeechConstant.ASR_PTT,
                sharedPreferences.getString("iat_punc_preference", "0"));

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        speechRecognizer.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        speechRecognizer.setParameter(SpeechConstant.ASR_AUDIO_PATH,
                Environment.getExternalStorageDirectory() + "/msc/iat.wav");

    }


    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.INTERNET
    };
}
