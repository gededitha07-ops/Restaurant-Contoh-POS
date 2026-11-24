package id.restaurantcontoh.pos;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
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

        webView.addJavascriptInterface(new PrinterBridge(), "AndroidPrinter");

        webView.setWebViewClient(new WebViewClient());
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
    protected void onDestroy() {
        closePrinter();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
