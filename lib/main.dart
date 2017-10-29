import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(new HelloApp());
}

class HelloApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Flutter Demo',
      theme: new ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: new HelloPage(title: 'Hello Android NDK'),
    );
  }
}

class HelloPage extends StatefulWidget {
  HelloPage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _HelloPageState createState() => new _HelloPageState();
}

class _HelloPageState extends State<HelloPage> {
  static const MethodChannel methodChannel =
      const MethodChannel('human.cues/hello');

  static const String javaMethod = 'helloJava';
  static const String cppMethod = 'helloCpp';

  String _javaResponse = "NOPE";
  String _cppResponse = "NOPE";

  bool callCpp = false;

  Future<Null> _hello() async {
    String response;
    try {
      response =
          await methodChannel.invokeMethod(callCpp ? cppMethod : javaMethod);
    } on PlatformException {
      response = (callCpp ? "c++" : "Java") + " didn't respond";
    } on MissingPluginException {
      response = "The line is blank...";
    }
    setState(() {
      if (callCpp)
        _cppResponse = response;
      else
        _javaResponse = response;
    });

    callCpp = !callCpp;
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
      appBar: new AppBar(
        title: new Text(widget.title),
      ),
      body: new Center(
        child: new Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            new Text(
              'Native Java, are you there?',
            ),
            new Text(
              _javaResponse,
              style: Theme.of(context).textTheme.display1,
            ),
            new Text(
              'Native c++, are you there?',
            ),
            new Text(
              _cppResponse,
              style: Theme.of(context).textTheme.display1,
            ),
          ],
        ),
      ),
      floatingActionButton: new FloatingActionButton(
        child: new Icon(callCpp ? Icons.settings_remote : Icons.settings_phone),
        onPressed: _hello,
        tooltip: 'Call Java',
      ),
    );
  }
}
