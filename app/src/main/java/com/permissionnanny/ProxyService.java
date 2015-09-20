package com.permissionnanny;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.support.v4.util.ArrayMap;
import com.permissionnanny.common.BundleUtil;
import com.permissionnanny.data.OngoingRequestDB;
import com.permissionnanny.lib.Nanny;
import com.permissionnanny.lib.request.RequestParams;
import com.permissionnanny.lib.request.simple.LocationRequest;
import com.permissionnanny.simple.ProxyGpsStatusListener;
import com.permissionnanny.simple.ProxyLocationListener;
import com.permissionnanny.simple.ProxyNmeaListener;
import io.snapdb.CryIterator;
import timber.log.Timber;

import javax.inject.Inject;
import java.security.SecureRandom;
import java.util.Map;

/**
 *
 */
public class ProxyService extends BaseService {

    public static final String CLIENT_ADDR = "clientAddr";
    public static final String REQUEST_PARAMS = "requestParams";

    private Map<String, ProxyClient> mClients = new ArrayMap<>();
    private AckReceiver mAckReceiver = new AckReceiver();
    private String mAckServerAddr;

    @Inject OngoingRequestDB mDB;

    @Override
    public void onCreate() {
        super.onCreate();
        getComponent(this).inject(this);
        mAckServerAddr = Long.toString(new SecureRandom().nextLong());
        registerReceiver(mAckReceiver, new IntentFilter(mAckServerAddr));
        Timber.wtf("init service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { // Service killed by OS? Restore client state
            restoreState();
            return super.onStartCommand(null, flags, startId);
        }
        Timber.wtf("Server started with args: " + BundleUtil.toString(intent));
        String clientId = intent.getStringExtra(CLIENT_ADDR);
        RequestParams requestParams = intent.getParcelableExtra(REQUEST_PARAMS);
        cacheRequest(clientId, requestParams);
        handleRequest(clientId, requestParams);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mAckReceiver);
    }

    private void cacheRequest(String clientId, RequestParams request) {
        mDB.putOngoingRequest(clientId, request);
    }

    private void restoreState() {
        int count = 0;
        CryIterator<? extends RequestParams> iterator = mDB.getOngoingRequests();
        while (iterator.moveToNext()) {
            count++;
            String client = iterator.key();
            RequestParams params = iterator.val();
            handleRequest(client, params);
        }
        iterator.close();
        Timber.wtf("restored " + count + " clients");
    }

    private void handleRequest(String clientAddr, RequestParams requestParams) {
        Timber.wtf("handling client=" + clientAddr + " req=" + requestParams);
        ProxyListener listener;
        switch (requestParams.opCode) {
        case LocationRequest.ADD_GPS_STATUS_LISTENER:
            listener = new ProxyGpsStatusListener(this, clientAddr);
            break;
        case LocationRequest.ADD_NMEA_LISTENER:
            listener = new ProxyNmeaListener(this, clientAddr);
            break;
        case LocationRequest.REQUEST_LOCATION_UPDATES1:
            listener = new ProxyLocationListener.Api1(this, clientAddr);
            break;
        default:
            throw new UnsupportedOperationException("Unsupported opcode " + requestParams.opCode);
        }

        mClients.put(clientAddr, new ProxyClient(clientAddr, requestParams, listener));
        listener.register(this, requestParams);
    }

    public void removeProxyClient(String clientId) {
        mClients.remove(clientId);
        mDB.delOngoingRequest(clientId);
        if (mClients.isEmpty()) { // no more clients? kill service
            stopSelf();
        }
    }

    public String getAckAddress() {
        return mAckServerAddr;
    }

    class AckReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: validate
            String clientAddr = intent.getStringExtra(Nanny.CLIENT_ADDRESS);
            ProxyClient client = mClients.get(clientAddr);
            if (client != null) {
                client.mListener.updateAck(SystemClock.elapsedRealtime());
            }
        }
    }
}
