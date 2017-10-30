package com.yourcompany.humancuestaggame;

import java.lang.Exception;

import android.os.Bundle;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {
  private static final String CHANNEL = "human.cues/hello";

  static {
    System.loadLibrary("native-lib");
  }

  public native String helloJNI();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GeneratedPluginRegistrant.registerWith(this);

    new MethodChannel(getFlutterView(), CHANNEL).setMethodCallHandler(
        new MethodCallHandler() {
          @Override
          public void onMethodCall(MethodCall call, Result result) {
            if (call.method.equals("helloJava")) {
              result.success("Java says hello!");
            } else if (call.method.equals("helloCpp")) {
              try {
                result.success(helloJNI());
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
