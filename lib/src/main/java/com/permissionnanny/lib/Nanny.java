package com.permissionnanny.lib;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Looper;
import com.permissionnanny.lib.request.PermissionRequest;
import com.permissionnanny.lib.request.content.ContentRequest;
import com.permissionnanny.lib.request.simple.LocationRequest;
import com.permissionnanny.lib.request.simple.WifiRequest;

/**
 * <h1>Permission Nanny</h1>
 * <p/>
 * Permission Nanny is an application that can access resources which are protected by Android permissions on your
 * behalf, so that your application does not need to declare permission usage in your AndroidManifest.xml. With
 * Permission Nanny, it is possible for your application to not require <b><i>any</i></b> permissions at all, yet still
 * be able to access permission-protected resources.
 * <p/>
 * From a high-level perspective, Permission Nanny acts as a proxy server between client applications and the Android
 * operating system. When a client needs to access a resource that is protected by Android permissions, the client will
 * send a request to Permission Nanny. Permission Nanny will then show a dialog to the user, asking the user for
 * authorization to grant the client access to the resource. If the user allows the request, Permission Nanny will
 * access the resource and return results to the client; if the user denies the request, Permission Nanny will simply
 * return an error response.
 * <p/>
 * <h1>Permission Police Protocol</h1>
 * <p/>
 * Clients communicate with Permission Nanny using the Permission Police Protocol (PPP). PPP is heavily inspired by HTTP
 * with a few minor tweaks, borrowing attributes such as status codes, headers and entity. PPP is implemented using
 * Intent broadcasts. There are 3 handshake flows depending on the type of the request - One-shot, Ongoing and Content.
 * <p/>
 * <i>If you are not interested in low-level details of how the handshakes are implemented and would like to know how to
 * make requests and listen for responses using the SDK or integrating your application with Permission Nanny, please
 * feel free to skip the rest and proceed to {@link PermissionRequest}</i>.
 * <p/>
 * <h2>One-shot Request Handshake Flow</h2>
 * <p/>
 * A request that accesses a resource at a single point in time - such as {@link WifiRequest#getConnectionInfo()} - is
 * considered a one-shot request.
 * <p/>
 * Clients start the flow by broadcasting an Intent to Permission Nanny's ClientRequestReceiver, a BroadcastReceiver
 * that validates incoming Intents. The Intent <b>must</b> contain a {@link #PROTOCOL_VERSION} String and an {@link
 * #ENTITY_BODY} Bundle containing valid request metadata. Request metadata <b>must</b> contain a {@link
 * #SENDER_IDENTITY} so that Permission Nanny knows who sent the request, {@link #REQUEST_PARAMS} to know what resource
 * to access and {@link #TYPE} to distinguish between {@link ContentRequest}s and non-{@link ContentRequest}s. Request
 * metadata <b>may</b> contain a {@link #REQUEST_REASON} String to explain to the user why the client needs access to
 * the resource. The request <b>may</b> also contain a {@link #CLIENT_ADDRESS} String to tell Permission Nanny where to
 * deliver the response. If {@link #CLIENT_ADDRESS} is empty, Permission Nanny will not return a response and you will
 * not know the status of your request.
 * <pre>
 *  {
 *      {@link #PROTOCOL_VERSION}*
 *      {@link #CLIENT_ADDRESS}
 *      {@link #ENTITY_BODY}* = {
 *          {@link #SENDER_IDENTITY}*
 *          {@link #TYPE}*
 *          {@link #REQUEST_PARAMS}*
 *          {@link #REQUEST_REASON}
 *      }
 *  }
 * </pre>
 * Permission Nanny will return an authorization response. The response Intent <b>must</b> contain a {@link
 * #PROTOCOL_VERSION} String, a {@link #STATUS_CODE} integer similar to HTTP, a {@link #CONNECTION} String fixed to
 * {@link #CLOSE} and a {@link #SERVER} String fixed to {@link #AUTHORIZATION_SERVICE}. If {@link #STATUS_CODE}
 * indicates success, the response <b>must</b> contain an {@link #ENTITY_BODY} Bundle containing the requested resource;
 * otherwise, the response <b>must</b> contain an {@link #ENTITY_ERROR}.
 * <pre>
 *  {
 *      {@link #PROTOCOL_VERSION}*
 *      {@link #STATUS_CODE}*
 *      {@link #CONNECTION}* = {@link #CLOSE}
 *      {@link #SERVER}* = {@link #AUTHORIZATION_SERVICE}
 *      {@link #ENTITY_BODY}
 *      {@link #ENTITY_ERROR}
 *  }
 * </pre>
 * <h4>Privacy and {@linkplain #CLIENT_ADDRESS}</h4>
 * <p/>
 * Because PPP is implemented using broadcast Intents, anyone with the correct IntentFilters could intercept the
 * communication between clients and Permission Nanny. To ensure no 3rd parties can intercept Permission Nanny's
 * responses, whenever the client makes a request, it uses SecureRandom to generate a nonce. The client's response
 * BroadcastReceiver will be listening for Intents whose action and nonce match. This nonce is known as the {@link
 * #CLIENT_ADDRESS} that the client's response receiver is listening on and is packaged in the request so that
 * Permission Nanny knows where to send responses to.
 * <p/>
 * <h2>Ongoing Request Handshake Flow</h2>
 * <p/>
 * A request that accesses a stream of resources over a period of time and requires an Android callback - such as {@link
 * LocationRequest#requestLocationUpdates(long, float, Criteria, LocationListener, Looper)} is considered an ongoing
 * request.
 * <p/>
 * The requirements for an ongoing request is exactly the same as one-shot requests.
 * <p/>
 * Permission Nanny will return one authorization response followed by a series of resource responses.
 * <p/>
 * <h4>Authorization Response</h4>
 * <p/>
 * Similar to one-shot responses, the authorization response <b>must</b> contain {@link #PROTOCOL_VERSION}, {@link
 * #STATUS_CODE} and {@link #SERVER}. But it's {@link #CONNECTION} String <b>must not</b> be set to {@link #CLOSE}. In
 * addition, it <b>must not</b> contain an {@link #ENTITY_BODY}; resources are packaged in the subsequent resource
 * responses. It <b>may</b> contain an {@link #ENTITY_ERROR} if the request failed.
 * <pre>
 *  {
 *      {@link #PROTOCOL_VERSION}*
 *      {@link #STATUS_CODE}*
 *      {@link #SERVER} = {@link #AUTHORIZATION_SERVICE}*
 *      {@link #ENTITY_ERROR}
 *  }
 * </pre>
 * <h4>Resource Response</h4>
 * <p/>
 * If the user authorizes the request, a series of resource responses will follow the authorization response. Resource
 * responses <b>must</b> contain {@link #PROTOCOL_VERSION}, {@link #STATUS_CODE}, {@link #SERVER} and {@link
 * #ENTITY_BODY}. {@link #SERVER} <b>must not</b> be set to {@link #AUTHORIZATION_SERVICE}. {@link #ENTITY_BODY}
 * <b>must</b> contain the requested resource and an {@link #ACK_SERVER_ADDRESS}, which is used by the client the send
 * acknowledgements for resource responses.
 * <pre>
 *  {
 *      {@link #PROTOCOL_VERSION}*
 *      {@link #STATUS_CODE}*
 *      {@link #SERVER}*
 *      {@link #ENTITY_BODY}* = {
 *          {@link #ACK_SERVER_ADDRESS}*
 *      }
 *      {@link #ENTITY_ERROR}
 *  }
 * </pre>
 * <h4>Acknowledging Ongoing Resource Responses</h4>
 * <p/>
 * When the client receives an ongoing resource response, it <b>must</b> let Permission Nanny know by sending an
 * acknowledgement Intent to Permission Nanny. If no acknowledgement is sent, Permission Nanny will deem the client
 * dormant, stop delivering resources and tell the client to close its connection. The client can make another request
 * to re-establish the connection.
 * <p/>
 * The acknowledgement <b>must</b> contain {@link #PROTOCOL_VERSION} and {@link #CLIENT_ADDRESS}.
 * <pre>
 *  {
 *      {@link #PROTOCOL_VERSION}*
 *      {@link #CLIENT_ADDRESS}*
 *  }
 * </pre>
 * <h2>Content Request Handshake Flow</h2>
 * <p/>
 * A request that accesses resources stored in {@link android.content.ContentProvider}s is considered a content
 * request.
 * <p/>
 * The requirements for a content request is exactly the same as one-shot requests. Clients start the flow by sending a
 * content request to Permission Nanny. If the user authorizes the request, Permission Nanny will return a content
 * response. The content response <b>must</b> contain an {@link #ENTITY_BODY} Bundle, which <b>must</b> only contain a
 * {@link #URI_PATH} String. The client <b>must</b> then make a 2nd request to Permission Nanny's content provider with
 * the {@link #URI_PATH} appended to the {@link #PROVIDER_AUTHORITY} as the Uri. The 2nd request will be handled by
 * Permission Nanny's ProxyContentProvider which will execute the content request and return results.
 * <pre>
 *  {
 *      {@link #PROTOCOL_VERSION}*
 *      {@link #STATUS_CODE}*
 *      {@link #CONNECTION}* = {@link #CLOSE}
 *      {@link #SERVER}* = {@link #AUTHORIZATION_SERVICE}
 *      {@link #ENTITY_BODY} = {
 *          {@link #URI_PATH}*
 *      }
 *      {@link #ENTITY_ERROR}
 *  }
 * </pre>
 * <h4>{@linkplain #URI_PATH} and {@linkplain android.database.CrossProcessCursorWrapper}</h4>
 * <p/>
 * Android 15 introduced the {@link android.database.CrossProcessCursorWrapper}, which facilitated sending Cursor data
 * between processes. This is the same mechanism Permission Nanny uses to deliver content resources to the client. After
 * the 1st handshake, Permission Nanny uses SecureRandom to generate a key to cache the query parameters; the key is
 * returned to the client as the {@link #URI_PATH}. The client then appends the key to the {@link #PROVIDER_AUTHORITY}
 * and starts the 2nd handshake with ProxyContentProvider.
 * <p/>
 * <i>If you would like to know how to make requests and listen for responses using the SDK or integrating your
 * application with Permission Nanny, please proceed to {@link PermissionRequest}</i>.
 */
public class Nanny {
    /** Request/Response field: Protocol version of the request the client is using. Type: {@link String} */
    @PPP public static final String PROTOCOL_VERSION = "Protocol-Version";
    /** Request/Response value: Permission Police Protocol v0.1 */
    @PPP public static final String PPP_0_1 = "PPP/0.1";

    /** Response field: Result status code. Type: int */
    @PPP public static final String STATUS_CODE = "Status-Code";
    /** Response value: Request succeeded. */
    @PPP public static final int SC_OK = 200;
    /** Response value: Server could not process request due to incorrect request parameters. */
    @PPP public static final int SC_BAD_REQUEST = 400;
    /** Response value: Client is not authorized to make a request. */
    @PPP public static final int SC_UNAUTHORIZED = 401;
    /** Response value: User denied your request. */
    @PPP public static final int SC_FORBIDDEN = 403;

    /** Request field: Address client is listening on. */
    @PPP public static final String CLIENT_ADDRESS = "Client-Address";

    /** Response field: Connection options. Type: {@link String} */
    @PPP public static final String CONNECTION = "Connection";
    /**
     * Response value: Client shall receive no further responses from the server; it is safe to unregister receivers or
     * unbind services.*
     */
    @PPP public static final String CLOSE = "Close";

    /** Response field: Service that handled the request. Type: {@link String} */
    @PPP public static final String SERVER = "Server";
    /** Response value: Service that authorizes requests. */
    @PPP public static final String AUTHORIZATION_SERVICE = "AuthorizationService";
    /** Response value: Service that delivers location updates. */
    @PPP public static final String LOCATION_SERVICE = "LocationService";
    /** Response value: Service that delivers GPS status updates. */
    @PPP public static final String GPS_STATUS_SERVICE = "GpsStatusService";
    /** Response value: Service that delivers NMEA updates. */
    @PPP public static final String NMEA_SERVICE = "NmeaService";

    /** Request/Response field: Resource payload. Type: {@link Bundle}. */
    @PPP public static final String ENTITY_BODY = "Entity-Body";
    /** Response field: Error payload. Type: {@link NannyException}. */
    @PPP public static final String ENTITY_ERROR = "Entity-Error";

    /** Entity field: */
    @PPP public static final String TYPE = "Type";
    /** Entity field: */
    @PPP public static final String REQUEST_PARAMS = "RequestParams";
    /** Entity field: */
    @PPP public static final String REQUEST_REASON = "RequestReason";
    /** Entity field: */
    @PPP public static final String SENDER_IDENTITY = "SenderIdentity";
    /** Entity field: */
    @PPP public static final String PERMISSION_MANIFEST = "PermissionManifest";
    /** Entity field: */
    @PPP public static final String ACK_SERVER_ADDRESS = "AckServerAddress";
    /** Entity field: */
    @PPP public static final String URI_PATH = "UriPath";

    // experimental
    /** Permission Nanny release build package name. */
    @PPP public static final String SERVER_PACKAGE = "com.permissionnanny";
    /** Permission Nanny debug build package name. */
    public static final String SERVER_DEBUG_PACKAGE = "com.permissionnanny.debug";
    /** Permission Nanny's app ID changes depending on the build type to avoid conflict with each other. */
    public static final String SERVER_APP_ID = BuildConfig.DEBUG ? SERVER_DEBUG_PACKAGE : SERVER_PACKAGE;

    /** Server Component that handles client requests. */
    @PPP public static final String CLIENT_REQUEST_RECEIVER = SERVER_PACKAGE + ".ClientRequestReceiver";

    /** Server Component that listens for client permission usages. */
    @PPP public static final String CLIENT_PERMISSION_MANIFEST_RECEIVER = SERVER_PACKAGE +
            ".ClientPermissionManifestReceiver";

    /** Broadcast Action: Sent when Permission Nanny wants to know which permissions clients are using. */
    @PPP public static final String ACTION_GET_PERMISSION_MANIFEST = SERVER_PACKAGE + ".GET_PERMISSION_MANIFEST";

    /** Authority that resolves to Permission Nanny's proxy content provider. */
    @PPP public static final String PROVIDER_AUTHORITY = SERVER_APP_ID + ".proxy_content_provider";

    /**
     * Checks if Permission Nanny is installed.
     *
     * @param context Activity, Service, etc.
     * @return {@code true} if Permission Nanny is installed
     */
    public static boolean isPermissionNannyInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo server = null;
        try {
            server = pm.getApplicationInfo(SERVER_APP_ID, 0);
        } catch (PackageManager.NameNotFoundException e) {/* Nothing to see here. */}
        return server != null;
    }
}
