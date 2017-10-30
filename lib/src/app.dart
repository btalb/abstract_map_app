import 'package:flutter/material.dart';

import './intro/intro_page.dart';

class App extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      title: 'Human Cues Tag Game',
      theme: new ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: new IntroPage(),
    );
  }
}
