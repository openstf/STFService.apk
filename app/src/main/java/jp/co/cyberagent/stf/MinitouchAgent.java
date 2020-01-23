/*
 *  Copyright (C) 2019 Orange
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package jp.co.cyberagent.stf;

import android.graphics.Point;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

import jp.co.cyberagent.stf.compat.InputManagerWrapper;
import jp.co.cyberagent.stf.compat.WindowManagerWrapper;
import jp.co.cyberagent.stf.util.InternalApi;

@RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MinitouchAgent extends Thread {
    private static final String TAG = MinitouchAgent.class.getSimpleName();
    private static final String SOCKET = "minitouchagent";
    private static final int DEFAULT_MAX_CONTACTS = 10;
    private static final int DEFAULT_MAX_PRESSURE = 0;
    private final int width;
    private final int height;
    private LocalServerSocket serverSocket;
    private long lastMouseDown;
    private int lastX = 0, lastY = 0;
    private final MotionEvent.PointerProperties[] pointerProperties = {new MotionEvent.PointerProperties()};
    private final MotionEvent.PointerCoords[] pointerCoords = {new MotionEvent.PointerCoords()};
    private final InputManagerWrapper inputManager;
    private final WindowManagerWrapper windowManager;
    private final Handler handler;

    /**
     * Get the width and height of the display by getting the DisplayInfo through reflection
     * Using the android.hardware.display.DisplayManagerGlobal but there might be other ways.
     *
     * @return a Point whose x is the width and y the height of the screen
     */
    public static Point getScreenSize() {
        Object displayManager = InternalApi.getSingleton("android.hardware.display.DisplayManagerGlobal");
        try {
            Object displayInfo = displayManager.getClass().getMethod("getDisplayInfo", int.class)
                .invoke(displayManager, Display.DEFAULT_DISPLAY);
            Class<?> cls = displayInfo.getClass();
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            return new Point(width, height);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Keep a way to start only the MinitouchAgent for debugging purpose
     */
    public static void main(String[] args) {
        //To create a Handler our main thread has to prepare the Looper
        Looper.prepare();
        Handler handler = new Handler();
        Point size = getScreenSize();
        MinitouchAgent m = new MinitouchAgent(size.x, size.y, handler);
        m.run();
        Looper.loop();
    }

    private void injectEvent(InputEvent event) {
        handler.post(() -> inputManager.injectInputEvent(event));
    }

    private MotionEvent getMotionEvent(int action, int buttonState, int x, int y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            lastMouseDown = now;
        }

        MotionEvent.PointerCoords coords = pointerCoords[0];
        int rotation = windowManager.getRotation();
        double rad = Math.toRadians(rotation * 90);
        coords.x = (float)(x * Math.cos(-rad) - y * Math.sin(-rad));
        coords.y = (rotation * width)+(float)(x * Math.sin(-rad) + y * Math.cos(-rad));
        MotionEvent event = MotionEvent.obtain(lastMouseDown, now, action, 1, pointerProperties,
            pointerCoords, 0, buttonState, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0);
        return event;
    }

    public MinitouchAgent(int width, int height, Handler handler) {
        this.width = width;
        this.height = height;
        this.handler = handler;
        inputManager = new InputManagerWrapper();
        windowManager = new WindowManagerWrapper();
    }

    @Override
    public void run() {
        try {
            Log.i(TAG, String.format("creating socket %s", SOCKET));
            serverSocket = new LocalServerSocket(SOCKET);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        manageClientConnection();
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Manages the client connection. The client is supposed to be minitouch.
     */
    private void manageClientConnection() {
        while (true) {
            try {
                Log.i(TAG, String.format("Listening on %s", SOCKET));
                LocalSocket clientSocket = serverSocket.accept();
                Log.d(TAG, "client connected");
                try {

                    MotionEvent.PointerProperties props = pointerProperties[0];
                    props.id = 0;
                    props.toolType = MotionEvent.TOOL_TYPE_FINGER;

                    MotionEvent.PointerCoords coords = pointerCoords[0];
                    coords.orientation = 0;
                    coords.pressure = 1;
                    coords.size = 1;

                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    OutputStreamWriter out = new OutputStreamWriter(clientSocket.getOutputStream());
                    out.write("v 1\n");
                    String resolution = String.format(Locale.getDefault(), "^ %d %d %d %d\n",
                        DEFAULT_MAX_CONTACTS, width, height, DEFAULT_MAX_PRESSURE);
                    out.write(resolution);
                    out.flush();
                    String cmd;
                    while ((cmd = in.readLine()) != null) {
                        processCommand(cmd);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    /**
     * processCommand parses touch related commands sent by stf
     * and inject them in Android InputManager.
     * Commmands can be of type down, up, move, commit
     * Note that it currently doesn't support multitouch
     *
     * @param cmd a String describing a touch event
     */
    private void processCommand(String cmd) {
        Scanner scanner = new Scanner(cmd);
        scanner.useDelimiter(" ");
        String type = scanner.next();
        try {
            switch (type) {
                case "c":
                    break;
                case "u":
                    scanner.nextInt(); //contact is currently not supported, walk through
                    injectEvent(getMotionEvent(MotionEvent.ACTION_UP, MotionEvent.BUTTON_PRIMARY, lastX, lastY));
                    break;
                case "d":
                    scanner.nextInt(); //contact is currently not supported, walk through
                    lastX = scanner.nextInt();
                    lastY = scanner.nextInt();
                    //scanner.nextInt(); //pressure is currently not supported
                    injectEvent(getMotionEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, lastX, lastY));
                    break;
                case "m":
                    scanner.nextInt(); //contact is currently not supported, walk through
                    lastX = scanner.nextInt();
                    lastY = scanner.nextInt();
                    //scanner.nextInt(); //pressure is currently not supported
                    injectEvent(getMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_PRIMARY, lastX, lastY));
                    break;
                case "w":
                    int delayMs = scanner.nextInt();
                    Thread.sleep(delayMs);
                    break;
                default:
                    System.out.println("could not parse: " + cmd);
            }
        } catch (NoSuchElementException e) {
            System.out.println("could not parse: " + cmd);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
