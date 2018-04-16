package com.example.caspar.binancebot;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.event.ListenKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
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


public class MainActivity extends WearableActivity {


    private static final String TAG = "MAIN";

    List<String> currentAssets = new ArrayList<>();
    List<Asset> currentAssetsAsObject = new ArrayList<>();
    Map<String, Integer> createdAsset = new HashMap<>();
    Map<String, Order> openSellOrders = new HashMap<>();
    Map<String, Order> openBuyOrders = new HashMap<>();
    Map<String, Double> currentPrice = new HashMap<>();
    Map<String, String> currentChange = new HashMap<>();
    Map<String, Double> assetAmount = new HashMap<>();
    Map<String, Double> assetValueinBTC = new HashMap<>();

    private CustomAdapter customAdapter;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;

    private OkHttpClient client1;
    private BinanceApiAsyncRestClient client;

    private LinearLayout layout;
    private TextView info;
    private TextView btcText;

    private int counter = 0;
    private double klineId;

    private Intent intent;

    private WebSocket webSocket;

    private double btcHolding;

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
            output(code + " / " + reason);
        }
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            output("err" + t.getMessage());
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layout = findViewById(R.id.layout);
        client1 = new OkHttpClient();
        info = findViewById(R.id.info);
        btcText = findViewById(R.id.btcValue);

        //createAdapter();

        // Enables Always-on
        setAmbientEnabled();

        intent = new Intent(this, PriceService.class);


        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(getString(R.string.api_key), getString(R.string.secret));


        client = factory.newAsyncRestClient();


        client.ping(new BinanceApiCallback<Void>() {
            @Override
            public void onResponse(Void aVoid) {
                startBinanceConnection();
            }
        });






    }

    private void startBinanceConnection() {
        client.getAccount(new BinanceApiCallback<Account>() {
            @Override
            public void onResponse(Account account) {
                List<AssetBalance> assetBalances = account.getBalances();
                for (int i = 0; i < assetBalances.size(); i++){
                    AssetBalance assetBalance = assetBalances.get(i);
                    //just fow now so small balances are not pulled
                    //need to calculate btc price
                    String a = assetBalance.getAsset();
                    if (a.equals("BTC")){
                        btcHolding = Double.parseDouble(assetBalance.getFree()) + Double.parseDouble(assetBalance.getLocked());
                    }else {
                        if (Double.parseDouble(assetBalance.getLocked()) > 0 || Double.parseDouble(assetBalance.getFree()) > 1){
                            Log.e("info", assetBalance.toString());
                            String asset = assetBalance.getAsset().toLowerCase();
                            currentAssets.add(asset);
                            double amount = Double.parseDouble(assetBalance.getLocked()) + Double.parseDouble(assetBalance.getFree());
                            asset = asset + "btc";
                            assetAmount.put(asset.toUpperCase(), amount);
                        }else {
                            String asset = assetBalance.getAsset();
                            double amount = Double.parseDouble(assetBalance.getLocked()) + Double.parseDouble(assetBalance.getFree());
                            if (amount > 0){
                                assetAmount.put(asset.toUpperCase(), amount);
                            }
                        }
                    }

                }

                startOrderRequest();
            }

        });
    }

    private void createAdapter() {
        customAdapter = new CustomAdapter(currentAssetsAsObject);
        //recyclerView = findViewById(R.id.recyclerView);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(customAdapter);
    }

    private void startOrderRequest() {
        for (int i = 0; i < currentAssets.size(); i++){
            String s = currentAssets.get(i).toUpperCase() + "BTC";
            client.getOpenOrders(new OrderRequest(s), new BinanceApiCallback<List<Order>>() {
                @Override
                public void onResponse(List<Order> orders) {
                    if (orders.size() != 0){
                        Order order = orders.get(0);
                        String symbol = order.getSymbol();
                        String stopPrice = order.getStopPrice();
                        String side = order.getSide().toString(); // SELL or BUY
                        Log.e("INFO", "open Order: " + symbol + ", " + stopPrice + ", " + side);
                        if (side.equals("SELL")){
                            openSellOrders.put(symbol, order);
                        }else if (side.equals("BUY")) {
                            openBuyOrders.put(symbol, order);
                        }else {
                            //prevent crashing
                        }

                        Log.e("INFO", symbol);
                        counter++;
                    }else{
                        counter++;
                    }

                    if (counter == currentAssets.size()){
                        start();
                    }

                }


            });
        }


    }

    private void start() {
        Log.e("INFO", "websocket started");
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
    }

    private void output(final String txt){
        runOnUiThread(new Runnable() {
            @SuppressLint("ResourceAsColor")
            @Override
            public void run() {
                if (!txt.substring(0, 3).equals("err")) {
                    updateView(txt);
                } else {
                    info.setText("no internet connection");
                }
            }
        });
    }

    private void updateView(String txt) {
        try {

            JSONObject object = new JSONObject(txt);
            String[] assetReturn = object.getString("stream").split("@");
            String asset = assetReturn[0].toUpperCase();
            JSONObject data = object.getJSONObject("data");
            String type = data.getString("e");
            if (type.equals("24hrTicker")){
                String price = data.getString("a");
                String change = data.getString("P");
                if (!createdAsset.containsKey(asset)){
                    if (asset.equals("BTCUSDT") && !createdAsset.containsKey("BTCUSDT")){
                        newEntry(asset, price, change);
                    }else if (createdAsset.containsKey("BTCUSDT")){
                        newEntry(asset, price, change);

                    }else {

                    }
                }else{
                    //update the View
                    updateAsset(asset, price, change);

                    double btcValue = calculateBTCValue(asset, price);

                    assetValueinBTC.put(asset, btcValue);
                    double walletValue = 0;
                    for (Map.Entry<String, Double> entry : assetValueinBTC.entrySet())
                    {
                        walletValue = walletValue + entry.getValue();
                    }

                    walletValue = walletValue + btcHolding;
                    btcText.setText(Double.toString(walletValue));

                    //check if we got liquidated
                    if (openBuyOrders.containsKey(asset) || openSellOrders.containsKey(asset)){
                        checkOrderBook(asset, price);
                    }

                    //update lists
                    currentPrice.put(asset, Double.parseDouble(price));
                    currentChange.put(asset, change);

                }
            }else {
                checkPriceAction(asset, data);
            }

        } catch (JSONException e) {

            info.setText(txt + "####" + e.toString());
            webSocket.cancel();
            start();
            //info.setText("no info");
            e.printStackTrace();
        }
    }

    private double calculateBTCValue(String asset, String price) {

        if (!asset.equals("BTCUSDT")){
            double amount = assetAmount.get(asset);
            double btcValue = Double.parseDouble(price) * amount;

            return btcValue;
        }


        return 0;
    }

    private void checkPriceAction(String asset, JSONObject data) {

        try {
            JSONObject k = data.getJSONObject("k");
            String openPrice = k.getString("o");
            if (klineId != Double.parseDouble(openPrice)){
                String closePrice = k.getString("c");

                double per = Double.parseDouble(closePrice) / Double.parseDouble(openPrice);
                if (per > 1){
                    //up
                    per = (per - 1) * 100;
                    if (per > 1){
                        String content = asset + " is moving up";
                        notifyUser("INFO", content);
                        klineId = Double.parseDouble(openPrice);
                    }
                }else {
                    //down
                    per = (1 - per) * 100 * -1;
                    if (per < -1){
                        String content = asset + " is moving down";
                        notifyUser("INFO", content);
                        klineId = Double.parseDouble(openPrice);
                    }
                }
                Log.e("INFO", asset + per);
            }else {
                Log.e("INFO", "user already informed. dont notify again");
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void updateAsset(String asset, String price, String change) {
        for (int i = 0; i < currentAssetsAsObject.size(); i++){
            String name = currentAssetsAsObject.get(i).getName();
            if (name.equals(asset)){
                if (name.equals("BTCUSDT")){
                    Asset a = currentAssetsAsObject.get(i);
                    a.setChange(change);
                    String p = price.split("\\.")[0];
                    a.setPrice(p);
                    customAdapter.notifyDataSetChanged();
                }else {
                    Asset a = currentAssetsAsObject.get(i);
                    a.setChange(change);
                    a.setPrice(price);
                    customAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void newEntry(String asset, String price, String change) {
        Log.e("INFO", "found");
        Asset asset1 = new Asset();
        asset1.setPrice(price);
        asset1.setName(asset);
        asset1.setChange(change);
        //customAdapter.add(asset1,0);
        //customAdapter.notifyDataSetChanged();
        createdAsset.put(asset, 1);
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


        // The channel ID of the notification.
        String id = "my_channel_01";

        info.setText(content);


        long[] mVibratePattern = new long[]{0, 400, 200, 400};


        //vibrate manually because Channel doesn't seem to do anything
        Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(mVibratePattern, -1);

    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.e(TAG, "onPause called");

        intent.putExtra("assets", (Serializable) currentAssets);
        //todo: for now no short long notification
//        intent.putExtra("sellorders", (Serializable) openSellOrders);
//        intent.putExtra("buyorders", (Serializable) openBuyOrders);


        if (webSocket != null){
            webSocket.cancel();
            info.setText("websocket closed");
            Log.e("INFO", "websocket canceled");
        }
        startService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.e(TAG, "onResume called");
        //start();
        stopService(intent);
    }

}


