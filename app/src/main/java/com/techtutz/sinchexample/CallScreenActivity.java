package com.techtutz.sinchexample;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sinch.android.rtc.AudioController;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.calling.CallState;
import com.sinch.android.rtc.video.VideoCallListener;
import com.sinch.android.rtc.video.VideoController;

import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class CallScreenActivity extends BaseActivity {

    static final String TAG = CallScreenActivity.class.getSimpleName();
    static final String CALL_START_TIME = "callStartTime";
    static final String ADDED_LISTENER = "addedListener";

    private AudioPlayer mAudioPlayer;
    private Timer mTimer;
    private UpdateCallDurationTask mDurationTask;
    private boolean mMuted = false;
    private String mCallId;
    private long mCallStart = 0;
    private boolean mAddedListener = false;
    private boolean mVideoViewsAdded = false;

    private TextView mCallDuration;
    private TextView mCallState;
    private TextView mCallerName;
    private ImageView btn_mute;
    private Context context;

    private class UpdateCallDurationTask extends TimerTask {

        @Override
        public void run() {
            CallScreenActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateCallDuration();
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putLong(CALL_START_TIME, mCallStart);
        savedInstanceState.putBoolean(ADDED_LISTENER, mAddedListener);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mCallStart = savedInstanceState.getLong(CALL_START_TIME);
        mAddedListener = savedInstanceState.getBoolean(ADDED_LISTENER);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.callscreen);
        context = CallScreenActivity.this;
        mAudioPlayer = new AudioPlayer(this);
        mCallDuration = (TextView) findViewById(R.id.callDuration);
        mCallerName = (TextView) findViewById(R.id.remoteUser);
        mCallState = (TextView) findViewById(R.id.callState);
        ImageView endCallButton = (ImageView) findViewById(R.id.hangupButton);
        btn_mute = findViewById(R.id.btn_mute);


        mCallId = getIntent().getStringExtra(SinchService.CALL_ID);
        Log.e(TAG, "----------------" + mCallId);
     /*   mCallId= Prefs.getInstance(context).GetValue(SinchService.CALL_ID);
        Log.e(TAG,"----------------"+mCallId);*/

        if (savedInstanceState == null) {
            mCallStart = System.currentTimeMillis();
        }
        endCallButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                endCall();
            }
        });

        btn_mute.setOnClickListener(view -> {
            onLocalAudioMuteClicked();
        });

        findViewById(R.id.btn_switch_camera).setOnClickListener(view -> {
            SwitchCamera();
        });

    }

    @Override
    public void onServiceConnected() {
        Call call = getSinchServiceInterface().getCall(mCallId);
        if (call != null) {
            if (!mAddedListener) {
                call.addCallListener(new SinchCallListener());
                mAddedListener = true;
            }
        } else {
            Log.e(TAG, "Started with invalid callId, aborting.");
            finish();
        }

        updateUI();
    }

    //method to update video feeds in the UI
    private void updateUI() {
        if ((getSinchServiceInterface() == null) || (!getSinchServiceInterface().isStarted())) {
            return; // early
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
        } else {


            Call call = getSinchServiceInterface().getCall(mCallId);
            if (call != null) {
                mCallerName.setText(call.getRemoteUserId());
                mCallState.setText(call.getState().toString());
                if (call.getState() == CallState.ESTABLISHED) {
                    //when the call is established, addVideoViews configures the video to  be shown
                    addVideoViews();
                }
            }
        }


    }
    //stop the timer when call is ended
    @Override
    public void onStop() {
        super.onStop();
        mDurationTask.cancel();
        mTimer.cancel();
        removeVideoViews();
    }
    //start the timer for the call duration here
    @Override
    public void onStart() {
        super.onStart();
        mTimer = new Timer();
        mDurationTask = new UpdateCallDurationTask();
        mTimer.schedule(mDurationTask, 0, 500);
        updateUI();
    }

    @Override
    public void onBackPressed() {
        // User should exit activity by ending call, not by going back.
    }
    //method to end the call
    private void endCall() {
        mAudioPlayer.stopProgressTone();
        Call call = getSinchServiceInterface().getCall(mCallId);
        if (call != null) {
            call.hangup();
        }
        finish();
    }
    private String formatTimespan(long timespan) {
        long totalSeconds = timespan / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
    //method to update live duration of the call
    private void updateCallDuration() {
        if (mCallStart > 0) {
            mCallDuration.setText(formatTimespan(System.currentTimeMillis() - mCallStart));
        }
    }
    //method which sets up the video feeds from the server to the UI of the activity
    private void addVideoViews() {
        if (mVideoViewsAdded || getSinchServiceInterface() == null) {
            return; //early
        }
        final VideoController vc = getSinchServiceInterface().getVideoController();
        if (vc != null) {
            FrameLayout localView = findViewById(R.id.localVideo);
            localView.addView(vc.getLocalView());
            localView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    //this toggles the front camera to rear camera and vice versa
                    vc.toggleCaptureDevicePosition();
                }
            });

            FrameLayout view = findViewById(R.id.remoteVideo);
            view.addView(vc.getRemoteView());
            View remoteView = vc.getRemoteView();
            if (remoteView instanceof SurfaceView) {
                ((SurfaceView) remoteView).setZOrderOnTop(true);
            }
            mVideoViewsAdded = true;
        }
    }

    void SwitchCamera() {
        final VideoController vc = getSinchServiceInterface().getVideoController();
        if (vc != null) {
            vc.toggleCaptureDevicePosition();
        }
    }

    //removes video feeds from the app once the call is terminated
    private void removeVideoViews() {
        if (getSinchServiceInterface() == null) {
            return; // early
        }

        VideoController vc = getSinchServiceInterface().getVideoController();

        if (vc != null) {
            FrameLayout view = findViewById(R.id.remoteVideo);
            view.removeView(vc.getRemoteView());
            View remoteView = vc.getRemoteView();
            if (remoteView instanceof SurfaceView) {
                ((SurfaceView) remoteView).setZOrderOnTop(true);
            }
            FrameLayout localView = findViewById(R.id.localVideo);
            localView.removeView(vc.getLocalView());
            mVideoViewsAdded = false;
        }
    }

    public void onLocalAudioMuteClicked() {
        mMuted = !mMuted;
        // Stops/Resumes sending the local audio stream.
        // mRtcEngine.muteLocalAudioStream(mMuted);
        AudioController audioController = getSinchServiceInterface().getAudioController();
        if (mMuted) {
            audioController.disableSpeaker();
            mMuted = false;
        } else {
            audioController.enableSpeaker();
            mMuted = true;

        }
        int res = mMuted ? R.drawable.btn_mute : R.drawable.btn_unmute;
        btn_mute.setImageResource(res);
    }

    private class SinchCallListener implements VideoCallListener {

        @Override
        public void onCallEnded(Call call) {
            CallEndCause cause = call.getDetails().getEndCause();
            Log.d(TAG, "Call ended. Reason: " + cause.toString());
            mAudioPlayer.stopProgressTone();
            setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
            String endMsg = "Call ended: " + call.getDetails().toString();
            Toast.makeText(CallScreenActivity.this, endMsg, Toast.LENGTH_LONG).show();

            endCall();
        }

        @Override
        public void onCallEstablished(Call call) {
            Log.d(TAG, "Call established");
            mAudioPlayer.stopProgressTone();
            mCallState.setText(call.getState().toString());
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            AudioController audioController = getSinchServiceInterface().getAudioController();
            audioController.enableSpeaker();
            mCallStart = System.currentTimeMillis();
            Log.d(TAG, "Call offered video: " + call.getDetails().isVideoOffered());
        }

        @Override
        public void onCallProgressing(Call call) {
            Log.d(TAG, "Call progressing");
            mAudioPlayer.playProgressTone();
        }

        @Override
        public void onShouldSendPushNotification(Call call, List pushPairs) {
            // Send a push through your push provider here, e.g. GCM
        }

        @Override
        public void onVideoTrackAdded(Call call) {
            Log.d(TAG, "Video track added");
            addVideoViews();
        }

        @Override
        public void onVideoTrackPaused(Call call) {

        }

        @Override
        public void onVideoTrackResumed(Call call) {

        }
    }
}
