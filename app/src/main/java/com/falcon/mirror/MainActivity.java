package com.falcon.mirror;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "FalconMirror";
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Thread streamThread;
    private volatile boolean running = false;

    private String host = "192.168.1.2";
    private int port = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        Uri uri = getIntent().getData();
        if (uri != null) {
            String h = uri.getHost();
            int p = uri.getPort();
            if (h != null) host = h;
            if (p != -1) port = p;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        streamThread = new Thread(this::streamLoop, "FalconStreamThread");
        streamThread.start();
        Log.d(TAG, "Stream thread started");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (streamThread != null) {
            try {
                streamThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Log.d(TAG, "Stream thread stopped");
    }

    private void streamLoop() {
        Log.d(TAG, "Connecting to " + host + ":" + port);

        while (running) {
            Socket socket = null;
            DataInputStream in = null;

            try {
                socket = new Socket(host, port);
                socket.setSoTimeout(5000); // 5 ثانیه تایم‌اوت برای خواندن
                in = new DataInputStream(socket.getInputStream());

                Log.d(TAG, "Connected to server!");

                while (running) {
                    try {
                        // 1. خواندن سایز
                        int frameSize = in.readInt();
                        Log.d(TAG, "Received frame size: " + frameSize);

                        if (frameSize <= 0 || frameSize > 15 * 1024 * 1024) {
                            Log.w(TAG, "Invalid frame size: " + frameSize);
                            continue;
                        }

                        // 2. خواندن داده JPEG
                        byte[] frameData = new byte[frameSize];
                        in.readFully(frameData);
                        Log.d(TAG, "Frame data received: " + frameData.length + " bytes");

                        // 3. تبدیل به Bitmap
                        Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameSize);
                        if (bitmap == null) {
                            Log.e(TAG, "Failed to decode JPEG");
                            continue;
                        }

                        // 4. نمایش روی SurfaceView
                        Canvas canvas = surfaceHolder.lockCanvas();
                        if (canvas != null) {
                            Rect dst = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                            canvas.drawBitmap(bitmap, null, dst, null);
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        } else {
                            Log.w(TAG, "Canvas is null");
                        }

                        bitmap.recycle();

                    } catch (IOException e) {
                        Log.e(TAG, "IO error while reading frame: " + e.getMessage());
                        // اگر خطای خواندن رخ داد، از حلقه داخلی خارج می‌شیم تا دوباره وصل بشیم
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
                        break;
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "Connection error: " + e.getMessage());
            } finally {
                // بستن منابع
                try {
                    if (in != null) in.close();
                } catch (IOException e) { /* ignore */ }
                try {
                    if (socket != null) socket.close();
                } catch (IOException e) { /* ignore */ }
            }

            // قبل از تلاش مجدد، ۲ ثانیه صبر کن
            if (running) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Log.d(TAG, "Stream loop ended");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
