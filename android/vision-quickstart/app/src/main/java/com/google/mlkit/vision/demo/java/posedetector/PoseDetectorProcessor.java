/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.java.posedetector;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.MyCameraService;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.demo.java.posedetector.classification.PoseClassifierProcessor;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A processor to run pose detector. */
public class PoseDetectorProcessor
    extends VisionProcessorBase<PoseDetectorProcessor.PoseWithClassification> {
  private static final String TAG = "PoseDetectorProcessor";

  private final PoseDetector detector;

  private final boolean showInFrameLikelihood;
  private final boolean visualizeZ;
  private final boolean rescaleZForVisualization;
  private final boolean runClassification;
  private final boolean isStreamMode;
  private final Context context;
  private final Executor classificationExecutor;


//
//  private String COMMAND_VOLUME_UP = "input keyevent 24";
//  private String COMMAND_VOLUME_DOWN = "input keyevent 25";
//  private String COMMAND_CHANNEL_UP = "input keyevent 166";
//  private String COMMAND_CHANNEL_DOWN = "input keyevent 167";
  private int KEYCODE_VOL_UP = 24;
  private int KEYCODE_VOL_DOWN = 25;
  private int KEYCODE_CHANNEL_UP = 166;
  private int KEYCODE_CHANNEL_DOWN = 167;
  private static final int VOLUME_INCREASE = 1;
  private static final int VOLUME_DECREASE = -1;
  private static final int VOLUME_NOCHANGE = 0;
  private static final int CHANNEL_UP = 1;
  private static final int CHANNEL_DOWN = -1;
  private static final int CHANNEL_NOCHANGE = 0;

  AudioManager audioManager;
  private Thread mVolumeThread = null;
  Integer mVolStatus = VOLUME_NOCHANGE;
  boolean mVolThreadRunning = true;

  private Thread mChannelThread = null;
  Integer mChannelStatus = CHANNEL_NOCHANGE;
  boolean mChannelThreadRunning = true;

  private PoseClassifierProcessor poseClassifierProcessor;

  /**
   * Internal class to hold Pose and classification results.
   */
  protected static class PoseWithClassification {
    private final Pose pose;
    private final List<String> classificationResult;

    public PoseWithClassification(Pose pose, List<String> classificationResult) {
      this.pose = pose;
      this.classificationResult = classificationResult;
    }

    public Pose getPose() {
      return pose;
    }

    public List<String> getClassificationResult() {
      return classificationResult;
    }
  }

  public PoseDetectorProcessor(
          Context context,
          PoseDetectorOptionsBase options,
          boolean showInFrameLikelihood,
          boolean visualizeZ,
          boolean rescaleZForVisualization,
          boolean runClassification,
          boolean isStreamMode) {
    super(context);
    this.showInFrameLikelihood = showInFrameLikelihood;
    this.visualizeZ = visualizeZ;
    this.rescaleZForVisualization = rescaleZForVisualization;
    detector = PoseDetection.getClient(options);
    this.runClassification = runClassification;
    this.isStreamMode = isStreamMode;
    this.context = context;
    classificationExecutor = Executors.newSingleThreadExecutor();
    audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

    startVolThread();
    startChannelThread();
  }

  private void startVolThread() {
    mVolumeThread = new Thread(new Runnable() {
      public void run() {
        while(mVolThreadRunning) {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          Log.i(TAG,"VIJESH VOLUME Thread: mVolStatus: "+mVolStatus);
          switch (mVolStatus) {
            case VOLUME_INCREASE:
              audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
              break;
            case VOLUME_DECREASE:
              audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
              break;
            case VOLUME_NOCHANGE:
              break;
          }
        }
        Log.e(TAG, "VIJESH exit thread !!!!!!!!!!!!");
      }
    });
    mVolumeThread.start();
  }

  private void startChannelThread() {
    mChannelThread = new Thread(new Runnable() {
      public void run() {
        while(mChannelThreadRunning) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          Log.i(TAG,"VIJESH CHANNEL Thread: mChannelStatus: "+ mChannelStatus);
          switch (mChannelStatus) {
            case CHANNEL_UP:
              sendKeyEvent(KEYCODE_CHANNEL_UP);
              break;
            case CHANNEL_DOWN:
              sendKeyEvent(KEYCODE_CHANNEL_DOWN);
              break;
            case CHANNEL_NOCHANGE:
              break;
          }
        }
        Log.e(TAG, "VIJESH Exit channel thread !!!!!!!!!!!!");
      }
    });
    mChannelThread.start();
  }

  @Override
  public void stop() {
    mVolThreadRunning = false;
    mChannelThreadRunning = false;
    mChannelThread = null;
    mVolumeThread = null;
    super.stop();
    detector.close();
  }

  @Override
  protected Task<PoseWithClassification> detectInImage(InputImage image) {
//    Log.w(TAG, "VIJESH detectInImage");
    return detector
            .process(image)
            .continueWith(
                    classificationExecutor,
                    task -> {
                      Pose pose = task.getResult();
                      List<String> classificationResult = new ArrayList<>();
                      if (runClassification) {
                        Log.w(TAG, "VIJESH : Classifying......");
                        if (poseClassifierProcessor == null) {
                          poseClassifierProcessor = new PoseClassifierProcessor(context, isStreamMode);
                        }
                        classificationResult = poseClassifierProcessor.getPoseResult(pose);
                      }
                      return new PoseWithClassification(pose, classificationResult);
                    });
  }

  @Override
  protected void onSuccess(
          @NonNull PoseWithClassification poseWithClassification,
          @NonNull GraphicOverlay graphicOverlay) {

//    Log.w(TAG, "VIJESH onsuccess");
    graphicOverlay.add(
            new PoseGraphic(
                    graphicOverlay, poseWithClassification.pose, showInFrameLikelihood, visualizeZ,
                    rescaleZForVisualization, poseWithClassification.classificationResult));

//vijesh : The result of pose detection is handled here. This is because, we are not creating any view to handle it in pose graphic.
    //VIJESH in case of activity based pose detection, logic is is posegraphic class. But for service we use below function only.
    processResult(poseWithClassification.pose);
  }

  //TODO VIJESH: this funciton should be mofied to add more features.
  private void processResult(Pose pose) {
    Log.i(TAG, "VIJESH processResult");
    List<PoseLandmark> landmarks = pose.getAllPoseLandmarks();
    if (landmarks.isEmpty()) {
      return;
    }
    PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
    PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
    PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
    PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);

    if (audioManager.isVolumeFixed()) {
      Log.e(TAG, "VIJESH: isVolumeFixed() true. Volume FIXED. May not change.... >>>>>>>");
    }
//    String handLocation;
//    String displayMessage = "Volume No-Change.....";
    float rhy = rightWrist.getPosition().y;
    float rhx = rightWrist.getPosition().x;
    float rsy = rightShoulder.getPosition().y;
    float rsx = rightShoulder.getPosition().x;

    if(rhx > rsx){
//      Log.e(TAG, "VIJESH:1 CHANNEL rhy"+rhy+"  rsy:"+rsy);
      if(rhy < (rsy + 100)){
        Log.i(TAG, "VIJESH:1 CHANNEL UP >>>>>>>>>>>>>>>");
        mChannelStatus = CHANNEL_UP;
      }else if(rhy < (rsy + 200)){
        Log.i(TAG, "VIJESH:1 CHANNEL DOWN >>>>");
        mChannelStatus = CHANNEL_DOWN;
      }else{
        Log.i(TAG, "VIJESH:1 CHANNEL No change");
        mChannelStatus = CHANNEL_NOCHANGE;
      }
    }else{
      Log.i(TAG, "VIJESH:1 CHANNEL No change");
      mChannelStatus = CHANNEL_NOCHANGE;
    }


    Log.i(TAG, "VIJESH: right Wrist: " + rightWrist.getPosition().y + " Shoulder: " + rightShoulder.getPosition().y);
    if(rhx < rsx) {
      if (rightWrist.getPosition().y < rightShoulder.getPosition().y - 50) {
        Log.i(TAG, "VIJESH:1 VOL UP >>>>>>>>>>>>>>>>>");
        mVolStatus = VOLUME_INCREASE;
        return; //don't process further commands
      } else if (rightWrist.getPosition().y > (rightShoulder.getPosition().y - 20)) {
        if (rightWrist.getPosition().y < (rightShoulder.getPosition().y + 100)) {
          Log.i(TAG, "VIJESH:1 VOL DOWN >>>>>>>");
          mVolStatus = VOLUME_DECREASE;
          return;//don't process further commands
        } else {
          mVolStatus = VOLUME_NOCHANGE;
          Log.i(TAG, "VIJESH:1 VOL NO-CHANGE");
        }
      } else {
        mVolStatus = VOLUME_NOCHANGE;
        Log.i(TAG, "VIJESH:1 VOL NO-CHANGE");
      }
    }else{
      mVolStatus = VOLUME_NOCHANGE;
      Log.i(TAG, "VIJESH:1 VOL NO-CHANGE");
    }


//    Left hand up/down for channel change.
//    if (leftWrist.getPosition().y < leftShoulder.getPosition().y - 100) {
//        Log.i(TAG, "VIJESH: LEFT hand is ABOVE ***********************************");
//        mChannelStatus = CHANNEL_UP;
//    } else if (leftWrist.getPosition().y > leftShoulder.getPosition().y - 20) {
//        if (leftWrist.getPosition().y < (leftShoulder.getPosition().y + 100)) {
//          Log.i(TAG, "VIJESH: left hand at SHOULDER *******");
//          mChannelStatus = CHANNEL_DOWN;
//        } else {
////          Log.i(TAG, "VIJESH: left hand is LOW *****");
//          mChannelStatus = CHANNEL_NOCHANGE;
//        }
//    }
  }


  private void sendKeyEvent(int keycode) {
    Intent intent = new Intent("com.sony.dtv.intent.action.KEY_CODE");
    intent.setPackage("com.sony.dtv.tvx");
    intent.putExtra("KeyCode", keycode);
    Log.i(TAG,"VIJESH Sending keycode to TVX: "+keycode);
    context.sendBroadcast(intent);
  }

  private void stopMyCameraService () {
      Log.i(TAG, "VIJESH: stopMyCameraService,,,,,,,,,,,,");
      Intent serviceIntent = new Intent(context, MyCameraService.class);
//    serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");
      context.stopService(serviceIntent);
    }
    @Override
    protected void onFailure (@NonNull Exception e){
      Log.e(TAG, "VIJESH Pose detection failed!", e);
    }
  }

