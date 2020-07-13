# STFService.apk

# Warning

This repository was superseded by https://github.com/DeviceFarmer/STFService.apk

**STFService.apk** is both an Android [Service](http://developer.android.com/guide/components/services.html) and a CLI-runnable "agent" application providing various services and event streams for [STF](https://github.com/openstf/stf). While it would usually make sense to separate these various services into their own components, we're trying to keep resource usage lower by bundling our services into a single package.

## Features

* [Protocol Buffers](https://github.com/google/protobuf) based socket interface
* Monitoring
    - Battery statistics
    - Network connectivity
    - Phone state
    - Device rotation
    - Airplane mode
    - Installed browser packages
* Actions
    - List user accounts
    - Remove user accounts
    - Get list of installed browsers
    - Get/set clipboard contents
    - Get display information
    - Get device IMEI number
    - Get device phone number
    - Get device ICCID number
    - Get device network operator
    - Get device network type
    - Get/set device ringer mode
    - Check whether an SD card is installed
    - Enable/disable WIFI, Bluetooth and query its status
    - Unlock/relock [KeyGuard](http://developer.android.com/reference/android/app/KeyguardManager.html) (essentially unlocking a device)
    - Acquire/release [WakeLock](http://developer.android.com/reference/android/os/PowerManager.WakeLock.html) (i.e. prevent a device from or allow it to sleep)
    - Invoke key events (with meta key support)
    - Set device rotation (optionally locking it)
* Misc tasks
    - Show a bright red screen with identifying information that wakes up and keeps the device on. Amazingly useful when you've got a whole bunch of devices you're connected to remotely, and you want to find the real, physical device.
* More.

## Requirements

* [Android Studio](http://developer.android.com/tools/studio/index.html)
* [ADB](http://developer.android.com/tools/help/adb.html) properly set up

## Building

Build with Gradle:

```bash
./gradlew assembleDebug
```

## Running

You'll need to [build](#building) first.

Like mentioned earlier, the service consists of two parts. The service is a normal Android Service, and is started with an activity. The agent part, however, requires permissions that are not available to 3rd party applications, but are available to ADB. So the agent is a "normal" Android CLI app. More on that later.

Also note that currently the debug keystore is fixed due to both convenience and laziness. Generate your own key if you feel like it.

### Install the .apk

First you need to install the APK. It contains both the service and the agent. This is pretty easy with Gradle:

```bash
./gradlew installDebug
```

Now you just need to start the two services.

### Running the service

There is no launcher icon for the service as it is meant to interfere with the system and its UI as little as possible. You do get a foreground notification, though, as background services without it usually get killed quite often.

Start the service via ADB as follows.

```bash
adb shell am startservice --user 0 \
    -a jp.co.cyberagent.stf.ACTION_START \
    -n jp.co.cyberagent.stf/.Service
```

If your device complains about the `--user` option, just remove it and try again. This happens on older devices.

You should now have the service running on the device.

Now we simply need to create a local forward so that we can eventually connect to the socket.

```bash
adb forward tcp:1100 localabstract:stfservice
```

Now you can connect to the socket using the local port. You may have more than one connection open at once, but it usually doesn't make much sense as you will just unnecessarily consume the surprisingly scarce USB resources. With that in mind, let's connect.

```bash
nc localhost 1100
```

This will give you binary output that will be explained in the [usage](#usage) section.

### Running the agent

Running the agent is a bit more complicated.

First, you must find out where the app got installed. We'll need the information very soon. Note that the path changed between every install, so you can't just use a static or cached value here.

The following command gets you the path.

```bash
APK=$(adb shell pm path jp.co.cyberagent.stf | \
    tr -d '\r' | awk -F: '{print $2}')
```

Now that you know the path, it's time to run the CLI app. Note that the app does not run in the background and will keep your shell occupied for as long as it runs.

```bash
adb shell export CLASSPATH="$APK"\; \
    exec app_process /system/bin jp.co.cyberagent.stf.Agent
```

Be very careful to note that this is a single command. The semicolon in the middle that would usually separate commands has been escaped.

Just like before, now we simply need to create a local forward so that we can eventually connect to the socket.

```bash
adb forward tcp:1090 localabstract:stfagent
```

Once again, you may have more than one connection open at once, but it usually doesn't make much sense as you will just unnecessarily consume your precious USB bandwidth. Anyway, let's connect.

```bash
nc localhost 1090
```

This, too, will give you binary output that will be explained in the [usage](#usage) section.

## Usage

It is assumed that you now have an open connection to both the service and the agent. If not, follow the [instructions](#running) above.

Both sockets use **delimited** [Protocol Buffers](https://github.com/google/protobuf) as their format. This means that you can send different types of requests over the same connection. Each request must include an ID that gets returned in the response. You can then map the response to the request on your side. Use timeouts to check for lost requests, although that doesn't really happen as long as you send valid messages to the right socket (more on that later).

You will also receive monitoring events that simply get pushed as they occur without anyone requesting anything.

At this point it would be useful if you checked out our [wire.proto](app/src/main/proto/wire.proto).

While both sockets use the same format, neither is able to respond to all requests. You must know which socket to send your request to. The agent is currently only able to respond to the following requests:

* `DO_KEYEVENT`
* `DO_TYPE`
* `DO_WAKE`
* `SET_ROTATION`

All other requests must go to the service. Furthermore, while all requests to the service require an ID (which must be unique within a reasonable time interval) and then receive a response with that ID, the agent currently does not provide any responses, and the ID is therefore not required.

The requests themselves are heavily wrapped. Quite honestly it's probably a bit over-engineered in that sense, and a bit of a pain to use. But it works, and you're able to receive replies and monitoring events at any time in any order, without having to care about waiting for the previous request to complete.

Take a look at the source to see the messages are sent and what you should expect. You can also take a look at [STF](https://github.com/openstf/stf) to see how the messages are being used in a [Node.js](https://nodejs.org/) application (although there are some abstractions in place).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

See [LICENSE](LICENSE).

Copyright © CyberAgent, Inc. All Rights Reserved.
