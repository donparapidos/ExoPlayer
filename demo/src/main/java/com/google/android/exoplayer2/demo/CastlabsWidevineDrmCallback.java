package com.google.android.exoplayer2.demo;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import com.google.android.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Created by Mario on 08/03/2017.
 */

public class CastlabsWidevineDrmCallback implements MediaDrmCallback {

	/**
	 * Base URI for the DRMtoday production environment
	 */
	public static final String DRMTODAY_PRODUCTION = "https://lic.drmtoday.com/license-proxy-widevine/cenc/";
	/**
	 * Base URI for the DRMtoday staging environment
	 */
	public static final String DRMTODAY_STAGING = "https://lic.staging.drmtoday.com/license-proxy-widevine/cenc/";
	/**
	 * Base URI for the DRMtoday test environment
	 */
	public static final String DRMTODAY_TEST = "https://lic.test.drmtoday.com/license-proxy-widevine/cenc/";

	/**
	 * Logger tag for this class
	 */
	private static final String TAG = "DrmCallback";

	/**
	 * The assetId of the requested asset.
	 */
	private static final String DRMTODAY_ASSET_ID_PARAM = "assetId";
	/**
	 * (optional) The variantId of the requested asset. If no variantId is used for identifying the asset, leave out the Query parameter.
	 */
	private static final String DRMTODAY_VARIANT_ID_PARAM = "variantId";
	/**
	 * Debug purposes.
	 */
	private static final String DRMTODAY_LOG_REQUEST_ID_PARAM = "logRequestId";
	/**
	 * All DRM Today calls use a random request Id that helps checking the request on the server.
	 * Print this request id on all logs when possible.
	 * This parameter should be generated and not manually set.
	 */
	private static final int REQUEST_ID_SIZE = 16;


	private final String drmTodayUrl;
	private final String assetId;
	private final String variantId;
	private final String merchant;
	private final String userId;
	private final String sessionId;
	private final String authToken;

	private final HttpDataSource.Factory dataSourceFactory;

	/**
	 * Create an instance of the DRMtoday Widevine callback.
	 *  @param drmTodayUrl The DRMtoday Widevine backend URL. One of {@link #DRMTODAY_PRODUCTION},
	 *                    {@link #DRMTODAY_STAGING}, or {@link #DRMTODAY_TEST}
	 * @param merchant    The merchant identifer (mandatory)
	 * @param assetId     The assetID for the content. This is not strictly required for Widevine but
 *                    needs to be set if multiple keys (i.e different keys for Audio/SD/HD) should be
 *                    handled by the same session.
	 * @param variantId   The variantID or {@code null}
	 * @param userId      The userID (mandatory)
	 * @param sessionId   The sessionID (mandatory)
	 * @param dataSourceFactory
	 */
	public CastlabsWidevineDrmCallback(final String drmTodayUrl, final String merchant, final String assetId,
		final String variantId, final String userId, final String sessionId, final Factory dataSourceFactory) {
		this(drmTodayUrl, merchant, assetId, variantId, userId, sessionId, null, dataSourceFactory);

	}

	/**
	 * Create an instance of the DRMtoday Widevine callback.
	 *
	 * @param drmTodayUrl The DRMtoday Widevine backend URL. One of {@link #DRMTODAY_PRODUCTION},
	 *                    {@link #DRMTODAY_STAGING}, or {@link #DRMTODAY_TEST}
	 * @param merchant    The merchant identifer (mandatory)
	 * @param assetId     The assetID for the content. This is not strictly required for Widevine but
	 *                    needs to be set if multiple keys (i.e different keys for Audio/SD/HD) should be
	 *                    handled by the same session.
	 * @param variantId   The variantID or {@code null}
	 * @param userId      The userID (mandatory)
	 * @param sessionId   The sessionID (mandatory)
	 * @param authToken   The auth token or {@code null} if callback with userID/sessionID is used
	 */
	public CastlabsWidevineDrmCallback(
		final String drmTodayUrl, final String merchant, final String assetId,
		final String variantId, final String userId, final String sessionId, final String authToken, Factory
		dataSourceFactory) {

		this.dataSourceFactory = dataSourceFactory;
		this.drmTodayUrl = drmTodayUrl;
		this.merchant = merchant;
		this.assetId = assetId;
		this.variantId = variantId;
		this.userId = userId;
		this.sessionId = sessionId;
		this.authToken = authToken;

		if (this.drmTodayUrl == null || this.drmTodayUrl.isEmpty()) {
			throw new IllegalArgumentException("No valid DRMtoday backend URL specified!");
		}
		if (this.merchant == null || this.merchant.isEmpty()) {
			throw new IllegalArgumentException("No valid merchant specified!");
		}
		if (this.userId == null || this.userId.isEmpty()) {
			throw new IllegalArgumentException("No valid userId specified!");
		}
		if (this.sessionId == null || this.sessionId.isEmpty()) {
			throw new IllegalArgumentException("No valid sessionId specified!");
		}
	}

	@Override
	public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
		String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData());
		return executePost(url, null, null);
	}

	@Override
	public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws IOException {
		Map<String, String> requestProperties = null;
		// We are ignoring the default URL that might be in the key requests and always go
		// to DRMtoday
		Uri.Builder builder = Uri.parse(drmTodayUrl).buildUpon();
		// Common parameters
		// We append a unique request ID
		builder.appendQueryParameter(DRMTODAY_LOG_REQUEST_ID_PARAM, generateRequestId());
		// If the asset ID and variant ID are configured, we append them
		// as query parameters to make sure that we can get all licenses (i.e. if you
		// have different keys for audio/SD/HD) for the given asset
		if (assetId != null) {
			builder.appendQueryParameter(DRMTODAY_ASSET_ID_PARAM, assetId);
		}
		if (variantId != null) {
			builder.appendQueryParameter(DRMTODAY_VARIANT_ID_PARAM, variantId);
		}

		// create a map for additional header parameters
		requestProperties = new HashMap<>();
		// Add the drmtoday custom data
		requestProperties.put("dt-custom-data",
			Base64.encodeToString(getCustomDataJSON().getBytes(), Base64.NO_WRAP));

		// if an auth token is configured, add it to the request headers
		if (authToken != null) {
			requestProperties.put("x-dt-auth-token", authToken);
		}
		// For Widevine requests, we have to make sure the content type is set accordingly
		requestProperties.put("Content-Type", "text/xml");

		// build the URI
		Uri uri = builder.build();

		// Execute the request and catch the most common errors
		final byte[] bytes;
		try {
			Log.i(TAG, "Executing DRMtoday request to : " + uri);
			bytes = executePost(uri.toString(), request.getData(), requestProperties);
		} catch (FileNotFoundException e) {
			throw new IOException("License not found");
		} catch (IOException e) {
			throw new IOException("Error during license acquisition", e);
		}

		try {
			JSONObject jsonObject = new JSONObject(new String(bytes));
			return Base64.decode(jsonObject.getString("license"), Base64.DEFAULT);
		} catch (JSONException e) {
			Log.e(TAG, "Error while parsing DRMtoday response: " + new String(bytes), e);
			throw new RuntimeException("Error while parsing response", e);
		}
	}

	/**
	 * @return json object that encodes the DRMtoday opt-data
	 */
	private String getCustomDataJSON() {
		JSONObject json = new JSONObject();
		try {
			json.put("userId", userId);
			json.put("sessionId", sessionId);
			json.put("merchant", merchant);
			return json.toString();
		} catch (JSONException e) {
			throw new RuntimeException("Unable to encode request data: " + e.getMessage(), e);
		}
	}

	/**
	 * Create a random request ID
	 */
	private static String generateRequestId() {
		byte[] byteArray = new byte[REQUEST_ID_SIZE];
		new Random().nextBytes(byteArray);
		StringBuilder sb = new StringBuilder(byteArray.length * 2);
		for (byte b : byteArray) {
			sb.append(String.format("%02x", b & 0xff));
		}
		return sb.toString();
	}

	private byte[] executePost(String url, byte[] data, Map<String, String> requestProperties)
		throws IOException {
		HttpDataSource dataSource = dataSourceFactory.createDataSource();
		if (requestProperties != null) {
			for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
				dataSource.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
			}
		}
		DataSpec dataSpec = new DataSpec(Uri.parse(url), data, 0, 0, C.LENGTH_UNSET, null,
			DataSpec.FLAG_ALLOW_GZIP);
		DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
		try {
			return Util.toByteArray(inputStream);
		} finally {
			inputStream.close();
		}
	}

}