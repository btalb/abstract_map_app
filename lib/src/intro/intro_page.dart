import 'package:flutter/material.dart';

class IntroPage extends StatefulWidget {
  @override
  _IntroPageState createState() => new _IntroPageState();
}

class _IntroPageState extends State<IntroPage> {
  @override
  Widget build(BuildContext context) {
    return new Scaffold(
        body: new Center(
            child: new Text("Intro Page",
                style: Theme.of(context).textTheme.display1)));
  }
}
