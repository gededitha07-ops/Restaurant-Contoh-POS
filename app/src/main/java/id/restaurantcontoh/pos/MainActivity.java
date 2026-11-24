package id.restaurantcontoh.pos;

import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 101;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private static final int REQUEST_BLUETOOTH = 100;
    private static final UUID PRINTER_UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        webView.addJavascriptInterface(new PrinterBridge(), "AndroidPrinter");

        webView.setWebViewClient(new WebViewClient());
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public boolean onShowFileChooser(
            WebView webView,
            ValueCallback<Uri[]> filePathCallback,
            FileChooserParams fileChooserParams) {

        if (MainActivity.this.filePathCallback != null) {
            MainActivity.this.filePathCallback.onReceiveValue(null);
        }

        MainActivity.this.filePathCallback = filePathCallback;

        try {
            Intent intent = fileChooserParams.createIntent();
            startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
            return true;
        } catch (Exception e) {
            MainActivity.this.filePathCallback = null;
            return false;
        }
    }
});

webView.loadUrl("file:///android_asset/index.html");
        requestBluetoothPermission();
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH);
            }
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public class PrinterBridge {

        @JavascriptInterface
        public String getPairedPrinters() {
            try {
                if (bluetoothAdapter == null) {
                    return "ERROR: Bluetooth is not supported.";
                }

                if (!hasBluetoothPermission()) {
                    return "ERROR: Bluetooth permission not granted.";
                }

                Set<BluetoothDevice> devices =
                    bluetoothAdapter.getBondedDevices();

                JSONArray list = new JSONArray();

                for (BluetoothDevice device : devices) {
                    JSONObject item = new JSONObject();
                    item.put("name", device.getName());
                    item.put("address", device.getAddress());
                    list.put(item);
                }

                return list.toString();

            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String connect(String address) {
            try {
                if (bluetoothAdapter == null) {
                    return "ERROR: Bluetooth is not supported.";
                }

                if (!hasBluetoothPermission()) {
                    return "ERROR: Bluetooth permission not granted.";
                }

                BluetoothDevice device =
                    bluetoothAdapter.getRemoteDevice(address);

                bluetoothSocket = device.createRfcommSocketToServiceRecord(
                    PRINTER_UUID
                );

                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();

                return "CONNECTED";

            } catch (Exception e) {
                closePrinter();
                return "ERROR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public String print(String data) {
            try {
                if (outputStream == null || bluetoothSocket == null
                        || !bluetoothSocket.isConnected()) {
                    return "ERROR: Printer is not connected.";
                }

                outputStream.write(data.getBytes("UTF-8"));
                outputStream.flush();

                return "PRINTED";

            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }
                @JavascriptInterface
        public String printLogo(String imageData, int paperWidth) {
            try {
                if (outputStream == null || bluetoothSocket == null
                        || !bluetoothSocket.isConnected()) {
                    return "ERROR: Printer is not connected.";
                }

                String base64 = imageData;

                if (base64.contains(",")) {
                    base64 = base64.substring(base64.indexOf(",") + 1);
                }

                byte[] imageBytes = Base64.decode(
                    base64, Base64.DEFAULT
                );

                Bitmap bitmap = BitmapFactory.decodeByteArray(
                    imageBytes, 0, imageBytes.length
                );

                if (bitmap == null) {
                    return "ERROR: Invalid logo image.";
                }

                int maxWidth = paperWidth == 58 ? 384 : 576;
                int height = Math.max(1,
                    bitmap.getHeight() * maxWidth / bitmap.getWidth());

                Bitmap resized = Bitmap.createScaledBitmap(
                    bitmap, maxWidth, height, true
                );

                int widthBytes = (maxWidth + 7) / 8;

                outputStream.write(new byte[]{
                    0x1B, 0x61, 0x01
                });

                outputStream.write(new byte[]{
                    0x1D, 0x76, 0x30, 0x00,
                    (byte)(widthBytes & 0xFF),
                    (byte)((widthBytes >> 8) & 0xFF),
                    (byte)(height & 0xFF),
                    (byte)((height >> 8) & 0xFF)
                });

                for (int y = 0; y < height; y++) {
                    for (int xByte = 0; xByte < widthBytes; xByte++) {
                        int value = 0;

                        for (int bit = 0; bit < 8; bit++) {
                            int x = xByte * 8 + bit;

                            if (x < maxWidth) {
                                int pixel = resized.getPixel(x, y);

                                int alpha = (pixel >> 24) & 0xFF;
                                int red = (pixel >> 16) & 0xFF;
                                int green = (pixel >> 8) & 0xFF;
                                int blue = pixel & 0xFF;

                                int gray = (red * 299 +
                                    green * 587 + blue * 114) / 1000;

                                if (alpha >= 128 && gray < 160) {
                                    value |= (0x80 >> bit);
                                }
                            }
                        }

                        outputStream.write(value);
                    }
                }

                outputStream.write(new byte[]{0x0A, 0x0A});
                outputStream.flush();

                resized.recycle();
                bitmap.recycle();

                return "PRINTED";

            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void disconnect() {
            closePrinter();
        }
    }

    private void closePrinter() {
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}

        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (Exception ignored) {}

        outputStream = null;
        bluetoothSocket = null;
    }
@Override
protected void onActivityResult(
        int requestCode, int resultCode, Intent data) {

    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
        if (filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams
                    .parseResult(resultCode, data);

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }
}
    @Override
    protected void onDestroy() {
        closePrinter();
        super.onDestroy();
    }

   @Override
public void onBackPressed() {
    if (webView != null && webView.canGoBack()) {
        webView.goBack();
    }
    // Jangan tutup aplikasi jika tidak ada halaman sebelumnya.
} 
}
