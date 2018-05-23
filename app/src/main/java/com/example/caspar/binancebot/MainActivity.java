package com.example.caspar.binancebot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderRequest;

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
    Map<String, Double> startPriceAsset = new HashMap<>();

    private CustomAdapter customAdapter;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;

    private OkHttpClient client1;
    private BinanceApiAsyncRestClient client;

    private LinearLayout layout;
    private TextView info;
    private TextView btcText;
    private boolean connection = true;

    private int counter = 0;
    private ArrayList<Double> klineId;

    private Intent intent;

    private WebSocket webSocket;

    private double btcHolding;
    private String btcPrice;


    //UI
    private TextView assetText;
    private TextView priceText;
    private TextView overallText;
    private TextView perText;
    private TextView allText;

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
        //info = findViewById(R.id.info);
        btcText = findViewById(R.id.btcValue);
        klineId = new ArrayList<>();


        priceText = findViewById(R.id.price);
        overallText = findViewById(R.id.overall);
        perText = findViewById(R.id.per);
        assetText = findViewById(R.id.asset);
        //allText = findViewById(R.id.all);

        //createAdapter();

        // Enables Always-on
        setAmbientEnabled();

        //intent = new Intent(this, PriceService.class);


        //create Binance Client and connect to server
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
                        //pretty tricky, because i want to hide dust.. but in this state i dont have the price of all assets
                        //to see if they are dust or not.. workaround with assets smaller than 0.05 will be ignored
                        if (Double.parseDouble(assetBalance.getLocked()) > 0 || Double.parseDouble(assetBalance.getFree()) > 0.05){
                            Log.e(TAG, assetBalance.toString());
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
                        Log.e(TAG, "open Order: " + symbol + ", " + stopPrice + ", " + side);
                        if (side.equals("SELL")){
                            openSellOrders.put(symbol, order);
                        }else if (side.equals("BUY")) {
                            openBuyOrders.put(symbol, order);
                        }else {
                            //prevent crashing
                        }

                        Log.e(TAG, symbol);
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
        Log.e(TAG, "websocket started");
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
                    //if the connection was lost we need to reconnect
                    //if we get back in here and connection is false we successfully reconnected
                    if (!connection){
                        connection = true;
                        assetText.setText("reconnected");
                    }
                    updateView(txt);
                } else {
                    connection = false;
                    assetText.setText("connection lost.. trying to reconnect");
                    webSocket.cancel();
                    start();
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

                    if (asset.equals("BTCUSDT")){
                        String first = price.split("\\.")[0];
                        btcPrice = first;
                    }

                    //update btc value
                    double btcValue = calculateBTCValue(asset, price);
                    assetValueinBTC.put(asset, btcValue);
                    double walletValue = 0;
                    //String all = "";
                    for (Map.Entry<String, Double> entry : assetValueinBTC.entrySet())
                    {
                        walletValue = walletValue + entry.getValue();
                        //all = all + entry.getKey() + ": " + (int) calculateOverallChange(asset, Double.toString(entry.getValue()));
                    }
                    //allText.setText(all);
                    walletValue = round(walletValue + btcHolding,5);
                    btcText.setText(Double.toString(walletValue) + ", $:" + btcPrice);

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
            assetText.setText(txt + "####" + e.toString());
            webSocket.cancel();
            start();
            e.printStackTrace();
        }
    }

    //calculate the change in percentage since startUp for every asset
    private double calculateOverallChange(String asset, String price) {

        if (startPriceAsset.get(asset) != null){
            double priceStart = startPriceAsset.get(asset);
            double per = priceStart / Double.parseDouble(price);
            per = (per - 1) * 100 * -1;
            return per;
        }else{
            return 0;
        }

    }

    //btc value without the dust
    private double calculateBTCValue(String asset, String price) {

        if (!asset.equals("BTCUSDT")){
            double amount = assetAmount.get(asset);
            double btcValue = Double.parseDouble(price) * amount;

            return btcValue;
        }

        return 0;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    private void checkPriceAction(String asset, JSONObject data) {

        try {
            JSONObject k = data.getJSONObject("k");
            String openPrice = k.getString("o");
            ImageView imageView = findViewById(R.id.imageView);
            //add the openPrice into a double list to prevent method begin called after the user
            //has already been notified
            if (!klineId.contains(Double.parseDouble(openPrice))){
                newPriceAction(asset, k, openPrice, imageView);
            }else {
                runningPriceAction(asset, k, openPrice, imageView);
                Log.e(TAG, "user already informed. dont notify again");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void runningPriceAction(String asset, JSONObject k, String openPrice, ImageView imageView) throws JSONException {
        //user is already informed about price action but it should still update until 5m candle closes

        String closePrice = k.getString("c");
        double per = Double.parseDouble(closePrice) / Double.parseDouble(openPrice);
        if (per > 1){
            //up
            per = (per - 1) * 100;
            if (per > 1){
                double overAll = round(calculateOverallChange(asset, closePrice),2);
                assetText.setText(asset);
                priceText.setText(closePrice);
                perText.setText("" + round(per, 2) + "%");
                if (overAll > 0){
                    overallText.setTextColor(Color.GREEN);
                }else{
                    overallText.setTextColor(Color.RED);
                }
                overallText.setText("" + (int) overAll);
                //String content = asset + " is moving up " + round(per, 2) + "% " + closePrice + " OA: " + overAll + "%";
                imageView.setBackgroundResource(R.drawable.ic_arrow_drop_up_black_24dp);
            }
        }else {
            //down
            per = (1 - per) * 100 * -1;
            if (per < -1){
                double overAll = round(calculateOverallChange(asset, closePrice),2);
                assetText.setText(asset);
                priceText.setText(closePrice);
                perText.setText("" + round(per, 2) + "%");
                if (overAll > 0){
                    overallText.setTextColor(Color.GREEN);
                }else{
                    overallText.setTextColor(Color.RED);
                }
                overallText.setText("" + (int) overAll);
                //String content = asset + " is moving down " + round(per, 2) + "% " + closePrice + " OA: " + overAll + "%";
                imageView.setBackgroundResource(R.drawable.ic_arrow_drop_down_black_24dp);
                //info.setText(content);
            }
        }
    }

    private void newPriceAction(String asset, JSONObject k, String openPrice, ImageView imageView) throws JSONException {
        String closePrice = k.getString("c");

        double per = Double.parseDouble(closePrice) / Double.parseDouble(openPrice);
        if (per > 1){
            //up
            per = (per - 1) * 100;
            if (per > 1){
                //shows percentage and price change for current asset
                double overAll = round(calculateOverallChange(asset, closePrice),2);
                assetText.setText(asset);
                priceText.setText(closePrice);
                perText.setText("" + round(per, 2) + "%");
                overallText.setTextColor(Color.GREEN);
                overallText.setText("" + (int) overAll);
                imageView.setBackgroundResource(R.drawable.ic_arrow_drop_up_black_24dp);
                notifyUser(1, "");
                klineId.add(Double.parseDouble(openPrice));
            } else if (per > 0.5) {
                double overAll = round(calculateOverallChange(asset, closePrice),2);
                assetText.setText(asset);
                priceText.setText(closePrice);
                perText.setText("" + round(per, 2) + "%");
                overallText.setTextColor(Color.GREEN);
                overallText.setText("" + (int) overAll);
            }
        }else {
            //down
            per = (1 - per) * 100 * -1;
            if (per < -1){
                double overAll = round(calculateOverallChange(asset, closePrice),2);
                assetText.setText(asset);
                priceText.setText(closePrice);
                perText.setText("" + round(per, 2) + "%");
                overallText.setTextColor(Color.RED);
                overallText.setText("" + (int) overAll);
                imageView.setBackgroundResource(R.drawable.ic_arrow_drop_down_black_24dp);
                notifyUser(-1, "");
                klineId.add(Double.parseDouble(openPrice));
            } else if (per < -0.5) {
                double overAll = round(calculateOverallChange(asset, closePrice),2);
                assetText.setText(asset);
                priceText.setText(closePrice);
                perText.setText("" + round(per, 2) + "%");
                overallText.setTextColor(Color.RED);
                overallText.setText("" + (int) overAll);
            }
        }
        Log.e(TAG, asset + per);
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
        Log.e(TAG, "found");
        Asset asset1 = new Asset();
        asset1.setPrice(price);
        asset1.setName(asset);
        asset1.setChange(change);
        //customAdapter.add(asset1,0);
        //customAdapter.notifyDataSetChanged();
        createdAsset.put(asset, 1);
        startPriceAsset.put(asset, Double.parseDouble(price));
    }

    private void checkOrderBook(String asset, String price) {
        if (openSellOrders.size() > 0){
            if (openSellOrders.containsKey(asset)){
                double stopPrice = Double.parseDouble(openSellOrders.get(asset).getStopPrice());
                double price1 = Double.parseDouble(price);
                if (openSellOrders.get(asset).getType().name().equals("STOP_LOSS_LIMIT")){
                    //stop loss hit
                    if (price1 < stopPrice){
                        Log.e(TAG, "long has been liquidated");
                        String notify = asset + " long has been liquidated at " + price;
                        assetText.setText(notify);
                        notifyUser(0, notify);
                        openSellOrders.remove(asset);
                    }
                }else {
                    //profit taken
                    if (price1 > stopPrice){
                        Log.e(TAG, "long has been closed");
                        String notify = asset + " long has been closed at " + price;
                        assetText.setText(notify);
                        notifyUser(0, notify);
                        openBuyOrders.remove(asset);
                    }
                }
            }else{
                double stopPrice = Double.parseDouble(openBuyOrders.get(asset).getStopPrice());
                double price1 = Double.parseDouble(price);
                if (openSellOrders.get(asset).getType().name().equals("STOP_LOSS_LIMIT")){
                    //stop loss hit
                    if (price1 > stopPrice){
                        Log.e(TAG, "short has been liquidated");
                        String notify = asset + " short has been liquidated at " + price;
                        assetText.setText(notify);
                        notifyUser(0, notify);
                        openBuyOrders.remove(asset);
                    }
                }else {
                    //profit taken
                    if (price1 < stopPrice){
                        Log.e(TAG, "short has been closed");
                        String notify = asset + " short has been closed at " + price;
                        assetText.setText(notify);
                        notifyUser(0, notify);
                        openBuyOrders.remove(asset);
                    }
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void notifyUser(int action, String content) {

        // The channel ID of the notification.
        String id = "my_channel_01";

        //info.setText(content);

        long[] mVibratePattern;
        if (action == 1){
            mVibratePattern = new long[]{0, 400, 200, 400};
        }else if (action == -1) {
            mVibratePattern = new long[]{0, 400};
        }else {
            mVibratePattern = new long[]{0, 400, 200, 400, 0, 400, 200, 400};
        }


        //vibrate manually because Channel doesn't seem to do anything
        Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(mVibratePattern, -1);

    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.e(TAG, "onPause called");

//        intent.putExtra("assets", (Serializable) currentAssets);
//        //todo: for now no short long notification
////        intent.putExtra("sellorders", (Serializable) openSellOrders);
////        intent.putExtra("buyorders", (Serializable) openBuyOrders);
//
//
//        if (webSocket != null){
//            webSocket.cancel();
//            info.setText("websocket closed");
//            Log.e(TAG, "websocket canceled");
//        }
//        startService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.e(TAG, "onResume called");
        //start();
        //stopService(intent);
    }

}


