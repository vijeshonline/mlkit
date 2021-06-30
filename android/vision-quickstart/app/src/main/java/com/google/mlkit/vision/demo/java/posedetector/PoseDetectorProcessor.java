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

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

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
  AudioManager audioManager;

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
  }

  @Override
  public void stop() {
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
    Log.w(TAG, "VIJESH processResult");
    List<PoseLandmark> landmarks = pose.getAllPoseLandmarks();
    if (landmarks.isEmpty()) {
      return;
    }
    PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
    PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);
    PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
    PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);

//    String handLocation;
//    String displayMessage = "Volume No-Change.....";
    Log.i(TAG,"VIJESH: right Wrist: " + rightWrist.getPosition().y + " Shoulder: " +rightShoulder.getPosition().y );
    if(rightWrist.getPosition().y < rightShoulder.getPosition().y){
      Log.i("PoseGraphic","VIJESH: right hand is above >>>>");
//      handLocation = "ABOVE";
//      displayMessage = "Volume UP";
      audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
    }else if(rightWrist.getPosition().y > (rightShoulder.getPosition().y)) {
      if(rightWrist.getPosition().y < (rightShoulder.getPosition().y + 100)) {
        Log.i(TAG, "VIJESH: right hand is BELOW SHOULDER >>>>>>>");
//        handLocation = "BELOW";
//        displayMessage = "Volume DOWN";
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
      }else if(leftWrist.getPosition().y < (leftShoulder.getPosition().y)) {
          Log.i(TAG, "VIJESH: LEFT hand is ABOVE ***********************************");
          stopMyCameraService();
      }else{
        Log.i(TAG, "VIJESH: right hand is very low");
//        displayMessage = "Volume No-Change.....";
      }
    }
  }
  private void stopMyCameraService() {
    Log.i(TAG,"VIJESH: stopMyCameraService,,,,,,,,,,,,");
    Intent serviceIntent = new Intent(context, MyCameraService.class);
//    serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");
    context.stopService(serviceIntent);
  }
  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Pose detection failed!", e);
    Log.e(TAG, "VIJESH Pose detection failed!", e);
  }
}
