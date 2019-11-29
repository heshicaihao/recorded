package com.heshicaihao.recorded.myactivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.heshicaihao.recorded.R;
import com.heshicaihao.recorded.activity.BaseActivity;
import com.heshicaihao.recorded.util.CameraHelp;
import com.heshicaihao.recorded.util.MyVideoEditor;
import com.heshicaihao.recorded.util.RecordUtil;
import com.heshicaihao.recorded.util.RxJavaUtil;
import com.heshicaihao.recorded.util.Utils;
import com.heshicaihao.recorded.view.MyRecordView;
import com.heshicaihao.recorded.view.RecordView;
import com.lansosdk.videoeditor.LanSoEditor;
import com.lansosdk.videoeditor.LanSongFileUtil;
import com.lansosdk.videoeditor.VideoEditor;
import com.lansosdk.videoeditor.onVideoEditorProgressListener;
import com.libyuv.LibyuvUtil;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.PermissionListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 仿微信录制视频
 * 基于ffmpeg视频编译
 * Created by heshicaihao on 19/6/18.
 */
public class MyRecordedActivity extends BaseActivity implements View.OnClickListener {

    public static final String INTENT_PATH = "intent_path";
    public static final String INTENT_DATA_TYPE = "result_data_type";

    public static final int RESULT_TYPE_VIDEO = 1;
    public static final int RESULT_TYPE_PHOTO = 2;

    public static final int REQUEST_CODE_KEY = 100;

    private SurfaceView surfaceView;
    private MyRecordView recordView;
    private ImageView iv_delete;
    private ImageView iv_next;
    private ImageView iv_change_camera;
    private ImageView iv_flash_video;
    private TextView editorTextView;
    private TextView tv_hint;

    private ArrayList<String> segmentList = new ArrayList<>();//分段视频地址
    private ArrayList<String> aacList = new ArrayList<>();//分段音频地址
    private ArrayList<Long> timeList = new ArrayList<>();//分段录制时间

    //是否在录制视频
    private AtomicBoolean isRecordVideo = new AtomicBoolean(false);
    //拍照
    private AtomicBoolean isShotPhoto = new AtomicBoolean(false);
    private CameraHelp mCameraHelp = new CameraHelp();
    private SurfaceHolder mSurfaceHolder;
    private MyVideoEditor mVideoEditor = new MyVideoEditor();
    private RecordUtil recordUtil;

    private int executeCount;//总编译次数
    private float executeProgress;//编译进度
    private String audioPath;
    private RecordUtil.OnPreviewFrameListener mOnPreviewFrameListener;

    private Timer timer;
    private TimerTask timerTask;
    private boolean isMyRecording = false ;
    public static final int MSG_TIME = 1;
    private int time = 0;


    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_TIME:
                    freshTime();
                    break;
                default:
                    break;
            }
        }
    };

    private void freshTime() {
        time++;
        tv_hint.setText(formatTime(time));

    }

    /**
     * 将秒转化为 HH:mm:ss 的格式
     *
     * @param time 秒
     * @return
     */
    private String formatTime(int time) {
        int hour = time / 3600;
        int min = time % 3600 / 60;
        int second = time % 60;

        return String.format(Locale.CHINESE, "%02d:%02d:%02d", hour, min, second);
    }

    private void statTime(){
        timer = new Timer();
        timerTask = new TimerTask(){
            @Override
            public void run() {
                if (isMyRecording){
                    Message msg = new Message();
                    msg.what = MSG_TIME;
                    //发送
                    handler.sendMessage(msg);
                }
            }
        };
        if(timer != null){
            timer.scheduleAtFixedRate(timerTask, 1000,1000);//严格按照时间执行
        }

    }

    private void stopTime(){
        if(timer!= null){
            timer.cancel();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_my_recorded);
        AndPermission.with(this).permission(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
                .requestCode(0).callback(new PermissionListener() {
            @Override
            public void onSucceed(int requestCode, @NonNull List<String> grantPermissions) {
            }

            @Override
            public void onFailed(int requestCode, @NonNull List<String> deniedPermissions) {
            }
        }).start();
        LanSoEditor.initSDK(this, null);
        LanSongFileUtil.setFileDir("/sdcard/heshicaihao/recorded/"+System.currentTimeMillis()+"/");
        LibyuvUtil.loadLibrary();

        initUI();
        initData();
        initMediaRecorder();
    }

    private void initUI() {

        surfaceView = findViewById(R.id.surfaceView);
        recordView = findViewById(R.id.recordView);
        iv_delete = findViewById(R.id.iv_delete);
        iv_next = findViewById(R.id.iv_next);
        iv_flash_video = findViewById(R.id.iv_flash_video);
        iv_change_camera = findViewById(R.id.iv_camera_mode);
        tv_hint = findViewById(R.id.tv_hint);

        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                int width = surfaceView.getWidth();
                int height = surfaceView.getHeight();
                float viewRatio = width*1f/height;
                float videoRatio = 9f/16f;
                ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
                if(viewRatio > videoRatio){
                    layoutParams.width = width;
                    layoutParams.height = (int) (width/viewRatio);
                }else{
                    layoutParams.width = (int) (height*viewRatio);
                    layoutParams.height = height;
                }
                surfaceView.setLayoutParams(layoutParams);
            }
        });
    }

    private void initMediaRecorder() {
        mCameraHelp.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if(isShotPhoto.get()){
                    isShotPhoto.set(false);
                    shotPhoto(data);
                }else{
                    if(isRecordVideo.get() && mOnPreviewFrameListener!=null){
                        mOnPreviewFrameListener.onPreviewFrame(data);
                    }
                }
            }
        });

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceHolder = holder;
                mCameraHelp.openCamera(mContext, Camera.CameraInfo.CAMERA_FACING_BACK, mSurfaceHolder);
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mCameraHelp.release();
            }
        });

        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraHelp.callFocusMode();
            }
        });

        mVideoEditor.setOnProgessListener(new onVideoEditorProgressListener() {
            @Override
            public void onProgress(VideoEditor v, int percent) {
                if(percent==100){
                    executeProgress++;
                }
                int pro = (int) (executeProgress/executeCount*100);
                editorTextView.setText("视频编辑中"+pro+"%");
            }
        });
    }

    private void shotPhoto(final byte[] nv21){

        TextView textView = showProgressDialog();
        textView.setText("图片截取中");
        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<String>() {
            @Override
            public String doInBackground() throws Throwable {

                boolean isFrontCamera = mCameraHelp.getCameraId()== Camera.CameraInfo.CAMERA_FACING_FRONT;
                int rotation;
                if(isFrontCamera){
                    rotation = 270;
                }else{
                    rotation = 90;
                }

                byte[] yuvI420 = new byte[nv21.length];
                byte[] tempYuvI420 = new byte[nv21.length];

                int videoWidth =  mCameraHelp.getHeight();
                int videoHeight =  mCameraHelp.getWidth();

                LibyuvUtil.convertNV21ToI420(nv21, yuvI420, mCameraHelp.getWidth(), mCameraHelp.getHeight());
                LibyuvUtil.compressI420(yuvI420, mCameraHelp.getWidth(), mCameraHelp.getHeight(), tempYuvI420,
                        mCameraHelp.getWidth(), mCameraHelp.getHeight(), rotation, isFrontCamera);

                Bitmap bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888);

                LibyuvUtil.convertI420ToBitmap(tempYuvI420, bitmap, videoWidth, videoHeight);

                String photoPath = LanSongFileUtil.DEFAULT_DIR+System.currentTimeMillis()+".jpeg";
                FileOutputStream fos = new FileOutputStream(photoPath);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();

                return photoPath;
            }
            @Override
            public void onFinish(String result) {
                closeProgressDialog();

                Intent intent = new Intent();
                intent.putExtra(INTENT_PATH, result);
                intent.putExtra(INTENT_DATA_TYPE, RESULT_TYPE_PHOTO);
                setResult(RESULT_OK, intent);
                finish();
            }
            @Override
            public void onError(Throwable e) {
                closeProgressDialog();
                Toast.makeText(getApplicationContext(), "图片截取失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initData() {
        recordView.setOnClickListener(this);
        iv_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSegment();
            }
        });

        iv_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editorTextView = showProgressDialog();
                executeCount = segmentList.size()+4;
                finishVideo();
            }
        });

        iv_flash_video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraHelp.changeFlash();
                if (mCameraHelp.isFlashOpen()) {
                    iv_flash_video.setImageResource(R.mipmap.video_flash_open);
                } else {
                    iv_flash_video.setImageResource(R.mipmap.video_flash_close);
                }
            }
        });

        iv_change_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCameraHelp.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK){
                    mCameraHelp.openCamera(mContext, Camera.CameraInfo.CAMERA_FACING_FRONT, mSurfaceHolder);
                }else{
                    mCameraHelp.openCamera(mContext, Camera.CameraInfo.CAMERA_FACING_BACK, mSurfaceHolder);
                }
                iv_flash_video.setImageResource(R.mipmap.video_flash_close);
            }
        });
    }

    public void finishVideo(){
        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<String>() {
            @Override
            public String doInBackground()throws Exception{
                //合并h264
                String h264Path = LanSongFileUtil.DEFAULT_DIR+System.currentTimeMillis()+".h264";
                Utils.mergeFile(segmentList.toArray(new String[]{}), h264Path);
                //h264转mp4
                String mp4Path = LanSongFileUtil.DEFAULT_DIR+""+System.currentTimeMillis()+".mp4";
                mVideoEditor.h264ToMp4(h264Path, mp4Path);
                //合成音频
                String aacPath = mVideoEditor.executePcmEncodeAac(syntPcm(), RecordUtil.sampleRateInHz, RecordUtil.channelCount);
                //音视频混合
                mp4Path = mVideoEditor.executeVideoMergeAudio(mp4Path, aacPath);
                return mp4Path;
            }
            @Override
            public void onFinish(String result) {
                closeProgressDialog();
                Toast.makeText(getApplicationContext(), "视频录制成功", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                closeProgressDialog();
                Toast.makeText(getApplicationContext(), "视频编辑失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String syntPcm() throws Exception{

        String pcmPath = LanSongFileUtil.DEFAULT_DIR+System.currentTimeMillis()+".pcm";
        File file = new File(pcmPath);
        file.createNewFile();
        FileOutputStream out = new FileOutputStream(file);
        for (int x=0; x<aacList.size(); x++){
            FileInputStream in = new FileInputStream(aacList.get(x));
            byte[] buf = new byte[4096];
            int len=0;
            while ((len=in.read(buf))>0){
                out.write(buf, 0, len);
                out.flush();
            }
            in.close();
        }
        out.close();
        return pcmPath;
    }

    private long videoDuration;
    private long recordTime;
    private String videoPath;
    private void startRecord(){

        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<Boolean>() {
            @Override
            public Boolean doInBackground() throws Throwable {
                videoPath = LanSongFileUtil.DEFAULT_DIR+System.currentTimeMillis()+".h264";
                audioPath = LanSongFileUtil.DEFAULT_DIR+System.currentTimeMillis()+".pcm";
                final boolean isFrontCamera = mCameraHelp.getCameraId()== Camera.CameraInfo.CAMERA_FACING_FRONT;
                final int rotation;
                if(isFrontCamera){
                    rotation = 270;
                }else{
                    rotation = 90;
                }
                recordUtil = new RecordUtil(videoPath, audioPath, mCameraHelp.getWidth(), mCameraHelp.getHeight(), rotation, isFrontCamera);
                return true;
            }
            @Override
            public void onFinish(Boolean result) {
                if(isMyRecording){
                    mOnPreviewFrameListener = recordUtil.start();
                    videoDuration = 0;
                    recordTime = System.currentTimeMillis();
                    runLoopPro();
                }else{
                    recordUtil.release();
                    recordUtil = null;
                }
            }
            @Override
            public void onError(Throwable e) {

            }
        });
    }

    private void runLoopPro(){

        RxJavaUtil.loop(20, new RxJavaUtil.OnRxLoopListener() {
            @Override
            public Boolean takeWhile(){
                return recordUtil!=null && recordUtil.isRecording();
            }
            @Override
            public void onExecute() {
                long currentTime = System.currentTimeMillis();
                videoDuration += currentTime - recordTime;
                recordTime = currentTime;
                long countTime = videoDuration;
                for (long time : timeList) {
                    countTime += time;
                }
            }
            @Override
            public void onFinish() {
                segmentList.add(videoPath);
                aacList.add(audioPath);
                timeList.add(videoDuration);
                initRecorderState();
            }
            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }
        });
    }

    private void upEvent(){
        if(recordUtil != null) {
            recordUtil.stop();
            recordUtil = null;
        }
        initRecorderState();
    }

    private void deleteSegment(){

        showConfirm("确认删除上一段视频?", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeProgressDialog();

                if(segmentList.size()>0 && timeList.size()>0) {
                    segmentList.remove(segmentList.size() - 1);
                    aacList.remove(aacList.size() - 1);
                    timeList.remove(timeList.size() - 1);
                }
                initRecorderState();
            }
        });
    }

    /**
     * 初始化视频拍摄状态
     */
    private void initRecorderState(){
        tv_hint.setText("开始录像");
        tv_hint.setVisibility(View.VISIBLE);

        iv_delete.setVisibility(View.VISIBLE);

        iv_next.setVisibility(View.VISIBLE);
    }

    /**
     * 清除录制信息
     */
    private void cleanRecord(){
        segmentList.clear();
        aacList.clear();
        timeList.clear();

        executeCount = 0;
        executeProgress = 0;

        iv_flash_video.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LanSongFileUtil.deleteFiles(new File(LanSongFileUtil.DEFAULT_DIR));
        cleanRecord();
        if(mCameraHelp != null){
            mCameraHelp.release();
        }
        if(recordUtil != null) {
            recordUtil.stop();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==RESULT_OK && data!=null){
            if(requestCode == REQUEST_CODE_KEY){
                Intent intent = new Intent();
                intent.putExtra(INTENT_PATH, data.getStringExtra(INTENT_PATH));
                intent.putExtra(INTENT_DATA_TYPE, RESULT_TYPE_VIDEO);
                setResult(RESULT_OK, intent);
                finish();
            }
        }else{
            cleanRecord();
            initRecorderState();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){

            case R.id.recordView:

                isMyRecording =!isMyRecording;
                if (isMyRecording){
                    //长按录像
                    isRecordVideo.set(true);
                    startRecord();
                    statTime();
                }else{
                    if(isRecordVideo.get()){
                        isRecordVideo.set(false);
                        upEvent();
                    }
                    stopTime();
                }
                break;

                default:

        }

    }
}
