package com.example.caspar.binancebot;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.binance.api.client.domain.account.Order;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static android.content.ContentValues.TAG;

/**
 * Created by Caspar on 12.04.18.
 */

public class PriceService extends Service {



    private OkHttpClient client1;
    Map<String, Order> openSellOrders = new HashMap<>();
    Map<String, Order> openBuyOrders = new HashMap<>();
    Map<String, Double> currentPrice = new HashMap<>();
    Map<String, String> currentChange = new HashMap<>();
    List<String> currentAssets = new ArrayList<>();

    private WebSocket webSocket;

    private final class EchoWebSocketListener extends WebSocketListener {
        private static final int NORMAL_CLOSURE_STATUS = 1000;
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            output(text);
        }
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            output("Receiving bytes : " + bytes.hex());
        }
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            output("Closing : " + code + " / " + reason);
        }
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            output("Error : " + t.getMessage());
        }
    }


    @Override
    public void onCreate() {

        super.onCreate();

        int i = 0;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        currentAssets = (List<String>) intent.getSerializableExtra("assets");
        openSellOrders = (Map<String, Order>) intent.getSerializableExtra("sellorders");
        openBuyOrders = (Map<String, Order>) intent.getSerializableExtra("buyorders");

        client1 = new OkHttpClient();

        StringBuilder stringBuilder = new StringBuilder("wss://stream.binance.com:9443/stream?streams=");
        for (int i = 0; i < currentAssets.size(); i++){
            stringBuilder.append(currentAssets.get(i) + "btc@ticker/");
            stringBuilder.append(currentAssets.get(i) + "btc@kline_5m/");
        }
        stringBuilder.append("btcusdt@ticker/");
        stringBuilder.append("btcusdt@kline_5m/");
        Request request = new Request.Builder().url(stringBuilder.toString()).build();
        EchoWebSocketListener listener = new EchoWebSocketListener();
        webSocket = client1.newWebSocket(request, listener);

        Log.e(TAG, "websocket started" );

        return flags;

    }

    //TODO: double websocket not needed
    private void output(final String txt){
        JSONObject object = null;
        try {
            object = new JSONObject(txt);
            String[] assetReturn = object.getString("stream").split("@");
            String asset = assetReturn[0].toUpperCase();
            JSONObject data = object.getJSONObject("data");
            String type = data.getString("e");
            if (type.equals("24hrTicker")){
                String price = data.getString("a");
                String change = data.getString("P");
                if (openBuyOrders.containsKey(asset) || openSellOrders.containsKey(asset)){
                    checkOrderBook(asset, price);
                }

                currentChange.put(asset, change);
            }else{
                checkPriceAction(asset, data);
            }



        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void checkPriceAction(String asset, JSONObject data) {

        try {
            JSONObject k = data.getJSONObject("k");
            String openPrice = k.getString("o");
            String closePrice = k.getString("c");

            double per = Double.parseDouble(closePrice) / Double.parseDouble(openPrice);
            if (per > 1){
                //up
                per = (per - 1) * 100;
                if (per > 1){
                    String content = asset + " is moving up";
                    notifyUser("INFO", content);
                }
            }else {
                //down
                per = (1 - per) * 100 * -1;
                if (per < -1){
                    String content = asset + " is moving down";
                    notifyUser("INFO", content);
                }
            }
            Log.e("INFO", asset + per);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private String checkOrderBook(String asset, String price) {
        if (openSellOrders.containsKey(asset)){
            double stopPrice = Double.parseDouble(openSellOrders.get(asset).getStopPrice());
            double price1 = Double.parseDouble(price);
            if (openSellOrders.get(asset).getType().name().equals("STOP_LOSS_LIMIT")){
                if (price1 < stopPrice){
                    Log.e("INFO", "long has been liquidated");
                    String notify = asset + " long has been liquidated at " + price;
                    notifyUser("SELL", notify);
                    openSellOrders.remove(asset);
                }
            }else {
                if (price1 > stopPrice){
                    Log.e("INFO", "long has been closed");
                    String notify = asset + " long has been closed at " + price;
                    notifyUser("BUY", notify);
                    openBuyOrders.remove(asset);
                }
            }
            return "-";
        }else{
            double stopPrice = Double.parseDouble(openBuyOrders.get(asset).getStopPrice());
            double price1 = Double.parseDouble(price);
            if (openSellOrders.get(asset).getType().name().equals("STOP_LOSS_LIMIT")){
                if (price1 > stopPrice){
                    Log.e("INFO", "short has been liquidated");
                    String notify = asset + " short has been liquidated at " + price;
                    notifyUser("BUY", notify);
                    openBuyOrders.remove(asset);
                }
            }else {
                if (price1 < stopPrice){
                    Log.e("INFO", "short has been closed");
                    String notify = asset + " short has been closed at " + price;
                    notifyUser("BUY", notify);
                    openBuyOrders.remove(asset);
                }
            }

            return "+";
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void notifyUser(String header, String content) {

        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        int notificationId = 001;
        // The channel ID of the notification.
        String id = "my_channel_01";


        android.support.v4.app.NotificationCompat.Builder notificationBuilder =
                new android.support.v4.app.NotificationCompat.Builder(this, id)
                        .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
                        .setContentTitle(header)
                        .setContentText(content);

        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel mChannel = new NotificationChannel(id, "Limit", importance);

        mChannel.enableVibration(true);
        long[] mVibratePattern = new long[]{0, 400, 200, 400};
        mChannel.setVibrationPattern(mVibratePattern);

        mNotificationManager.createNotificationChannel(mChannel);
        mNotificationManager.notify(notificationId, notificationBuilder.build());

        //vibrate manually because Channel doesn't seem to do anything
        Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(mVibratePattern, -1);

    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        webSocket.cancel();
    }
}
