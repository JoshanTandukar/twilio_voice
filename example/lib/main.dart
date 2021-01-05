import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:voice/voice.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  String _eventMessage;

  TextEditingController _toController, _fromController;

  @override
  void initState() {
    super.initState();

    initPlatformState();
    _toController = TextEditingController();
    _fromController = TextEditingController(text: "alice");
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      platformVersion = await Voice.platformVersion;
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  void _onEvent(Object event) {
    setState(() {
      _eventMessage =
      "Battery status: ${event == 'charging' ? '' : 'dis'}charging.";
    });
  }

  void _onError(Object error) {
    setState(() {
      _eventMessage = 'Battery status: unknown.';
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Twilio Voice Example'),
        ),
        body: SafeArea(
            child: Center(
              child: Padding(
                padding: EdgeInsets.all(20),
                child: Column(
                  children: <Widget>[
                    Padding(
                      padding: EdgeInsets.all(10),
                      child: Text('Running on: $_platformVersion\n'),
                    ),
                    Padding(
                      padding: EdgeInsets.all(10),
                      child: Text('Running on: $_eventMessage\n'),
                    ),
                    TextFormField(
                      controller: _fromController,
                      decoration: InputDecoration(
                          labelText: 'Sender Identifier or Phone Number'),
                    ),
                    Divider(),
                    TextFormField(
                      controller: _toController,
                      decoration: InputDecoration(
                          labelText: 'Receiver Identifier or Phone Number'),
                    ),
                    SizedBox(
                      height: 10,
                    ),
                    RaisedButton(
                      child: Text("Make Call"),
                      onPressed: () async {
                        Voice.phoneCallEventSubscription
                            .listen(_onEvent, onError: _onError);
                        Voice.receiveCalls(_fromController.text);
                        Voice.makeCall(
                            from: _fromController.text,
                            to: _toController.text,
                            accessTokenUrl: "https://{SERVER_URL}/accesstoken",
                            toDisplayName: "James Bond");
                      },
                    )
                  ],
                ),
              ),
            )),
      ),
    );
  }
}
