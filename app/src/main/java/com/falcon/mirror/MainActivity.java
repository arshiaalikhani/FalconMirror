package com.falcon.mirror;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Thread streamThread;
    private volatile boolean running = false;

    // آدرس پیش‌فرض (اگه لینک نزدن)
    private String host = "192.168.1.2";
    private int port = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // تمام‌صفحه + صفحه روشن بمونه
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        // پارس دیپ لینک: falcon://192.168.1.2:5000
        Uri uri = getIntent().getData();
        if (uri != null) {
            String h = uri.getHost();
            int p = uri.getPort();
            if (h != null) host = h;
            if (p != -1) port = p;
        }
    }

    // ============================================================
    //  SurfaceHolder Callbacks
    // ============================================================

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        streamThread = new Thread(this::streamLoop, "FalconStreamThread");
        streamThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try {
            if (streamThread != null) streamThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================
    //  حلقه اصلی استریم
    // ============================================================

    private void streamLoop() {
        while (running) {
            try (Socket socket = new Socket(host, port);
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                while (running) {
                    // 1. خوندن سایز فریم (4 بایت، big-endian)
                    int frameSize = in.readInt();

                    // بررسی سانیتی
                    if (frameSize <= 0 || frameSize > 15 * 1024 * 1024) continue;

                    // 2. خوندن داده JPEG
                    byte[] frameData = new byte[frameSize];
                    in.readFully(frameData);

                    // 3. دیکود JPEG به Bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameSize);
                    if (bitmap == null) continue;

                    // 4. رسم روی SurfaceView (کشیده میشه به اندازه صفحه)
                    Canvas canvas = surfaceHolder.lockCanvas();
                    if (canvas != null) {
                        Rect dst = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                        canvas.drawBitmap(bitmap, null, dst, null);
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                    bitmap.recycle();
                }

            } catch (IOException e) {
                // اتصال قطع شد، 2 ثانیه صبر کن دوباره امتحان کن
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
