package com.yourcompany.humancuestaggame;

import java.lang.Exception;
import java.lang.Runnable;

import android.os.Bundle;
import android.os.Handler;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {
  private static final String HELLO_CHANNEL = "human.cues/hello";
  private static final String TIME_CHANNEL = "human.cues/time";

  private int javaTime = 0;

  // static {
  //   System.loadLibrary("native-lib");
  // }

  // public native String helloJNI();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GeneratedPluginRegistrant.registerWith(this);

    new EventChannel(getFlutterView(), TIME_CHANNEL).setStreamHandler(
        new StreamHandler() {
          private Handler h;
          private Runnable r;
          private EventSink e;
          private static final int delay = 1000; // 1s

          @Override
          public void onListen(Object arguments, final EventSink events) {
            // Start the Handler, updating the time every second
            h = new Handler();
            r = new Runnable() {
              public void run() {
                javaTime++;
                events.success(String.valueOf(javaTime));
                h.postDelayed(this, delay);
              }
            };
            h.postDelayed(r, delay);
          }

          @Override
          public void onCancel(Object arguments) {
            // Stop the runnable
            h.removeCallbacks(r);
          }
        });

    new MethodChannel(getFlutterView(), HELLO_CHANNEL).setMethodCallHandler(
        new MethodCallHandler() {
          @Override
          public void onMethodCall(MethodCall call, Result result) {
            if (call.method.equals("helloJava")) {
              result.success("Java says hello!");
            } else if (call.method.equals("helloCpp")) {
              try {
                result.success("JNI OFF");
                //result.success(helloJNI());
              } catch (Exception e) {
                result.success("Java couldn't find c++");
              }
            } else {
              result.notImplemented();
            }
          }
        });

  }
}
