package com.google.android.exoplayer2.drm;

import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MediaCryptoMaker {
    private final static String TAG = "Exo_MediaCryptoMaker";

    private final static int MAKER_NO_ERROE = 0;
    private final static int MAKER_NO_SCHEME = 1;

    private final static String DRM_SCHEME_WIDEVINE = "widevine";
    private final static String DRM_SCHEME_PLAYREADY = "playready";
    private final static String DRM_SCHEME_CLEARKEY = "clearkey";

    private static final UUID CLEARKEY_UUID = new UUID(0xE2719D58A985B3C9L, 0x781AB030AF78D30EL);
    private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private static final UUID PLAYREADY_UUID = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);

    /*--------------------------------------------------------------------------------------------------------*/

    private static final String CENC_SCHEME_MIME_TYPE = "cenc";
    private static final String MOCK_LA_URL_VALUE = "https://x";
    private static final String MOCK_LA_URL = "<LA_URL>" + MOCK_LA_URL_VALUE + "</LA_URL>";
    private static final int UTF_16_BYTES_PER_CHARACTER = 2;

    /*--------------------------------------------------------------------------------------------------------*/
    //private List<DrmInitData.SchemeData> schemeData = schemeDatas = Collections.unmodifiableList(Assertions.checkNotNull(schemeDatas));

    private UUID uuid;
    private MediaDrm mediaDrm;
    private byte[] sessionId;
    private MediaCrypto mediaCrypto;

    private MediaDrm.KeyRequest keyRequest;
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public MediaCrypto make(String drmScheme, String defaultLicenseUrl, FormatHolder formatHolder){
        Log.d(TAG, "make drmScheme: " + drmScheme + " defaultLicenseUrl: "
                + defaultLicenseUrl + " format: " + Format.toLogString(formatHolder.format));
        uuid = getUUID(drmScheme);
/*        boolean forceAllowInsecureDecoderComponents = WIDEVINE_UUID.equals(uuid)
                && "L3".equals(mediaDrm.getPropertyString("securityLevel"));*/

        Log.d(TAG, "uuid: " + uuid.toString());
        List<DrmInitData.SchemeData> schemeDatas
                = getSchemeDatas(formatHolder.format.drmInitData, uuid, true);
        Log.d(TAG, "schemeDatas length: " + schemeDatas.size());

        try {
            mediaDrm = new MediaDrm(uuid);
            sessionId = mediaDrm.openSession();
            mediaCrypto = new MediaCrypto(uuid, sessionId);

            Log.d(TAG, "sessionId: " + new String(sessionId));

            DrmInitData.SchemeData schemeData = null;
            byte[] initData = null;
            String mimeType = null;
            if (schemeDatas != null) {
                schemeData = getSchemeData(uuid, schemeDatas);
                initData = adjustRequestInitData(uuid, Assertions.checkNotNull(schemeData.data));
                mimeType = adjustRequestMimeType(uuid, schemeData.mimeType);
                Log.d(TAG, "initData: " + new String(initData) + " mimeType: " + mimeType);
            }
            keyRequest = mediaDrm.getKeyRequest(sessionId, initData, mimeType,
                    MediaDrm.KEY_TYPE_STREAMING, /*optionalParameters*/null);
            byte[] response = executeKeyRequest(uuid, defaultLicenseUrl, keyRequest);
            Log.d(TAG, "response: " + new String(response));
            mediaDrm.provideKeyResponse(sessionId, response);
        } catch (UnsupportedSchemeException e) {
            e.printStackTrace();
        } catch (ResourceBusyException e) {
            e.printStackTrace();
        } catch (NotProvisionedException e) {
            e.printStackTrace();
        } catch (MediaCryptoException e) {
            e.printStackTrace();
        } catch (DeniedByServerException e) {
            e.printStackTrace();
        }

        return mediaCrypto;
    }

    private UUID getUUID(String drmScheme){
        if(drmScheme == null){
            Log.d(TAG, "drmScheme is null");
            return null;
        }
        switch (drmScheme.toLowerCase()){
            case DRM_SCHEME_WIDEVINE:
                return WIDEVINE_UUID;
            case DRM_SCHEME_PLAYREADY:
                return PLAYREADY_UUID;
            case DRM_SCHEME_CLEARKEY:
                return CLEARKEY_UUID;
            default:
                try {
                    return UUID.fromString(drmScheme);
                }catch (RuntimeException e){
                    Log.d(TAG, "getUUID error: " + e.toString());
                    return null;
                }
        }
    }


    private static final int MAX_MANUAL_REDIRECTS = 5;
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private byte[] executeKeyRequest(UUID uuid, String defaultLicenseUrl,
                                     MediaDrm.KeyRequest keyRequest){
        String url = keyRequest.getDefaultUrl();
        if(defaultLicenseUrl != null && TextUtils.isEmpty(url)){
            url = defaultLicenseUrl;
        }

        String agent = "ExoPlayerDemo/2.10.3 (Linux;Android 9) ExoPlayerLib/2.10.3";//Util.getUserAgent(this, "ExoPlayerDemo");
        HttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory(agent);
        HttpDataSource dataSource = dataSourceFactory.createDataSource();

        Map<String, String> requestProperties = new HashMap<>();
        // Add standard request properties for supported schemes.
        String contentType = C.PLAYREADY_UUID.equals(uuid) ? "text/xml"
                : (C.CLEARKEY_UUID.equals(uuid) ? "application/json" : "application/octet-stream");
        requestProperties.put("Content-Type", contentType);
        if (C.PLAYREADY_UUID.equals(uuid)) {
            requestProperties.put("SOAPAction",
                    "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
        }

        if (requestProperties != null) {
            for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                dataSource.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
            }
        }

        int manualRedirectCount = 0;
        while (true) {
            DataSpec dataSpec =
                    new DataSpec(
                            Uri.parse(url),
                            keyRequest.getData(),
                            /* absoluteStreamPosition= */ 0,
                            /* position= */ 0,
                            /* length= */ C.LENGTH_UNSET,
                            /* key= */ null,
                            DataSpec.FLAG_ALLOW_GZIP);
            DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
            try {
                return Util.toByteArray(inputStream);
            } catch (HttpDataSource.InvalidResponseCodeException e) {
                // For POST requests, the underlying network stack will not normally follow 307 or 308
                // redirects automatically. Do so manually here.
                boolean manuallyRedirect =
                        (e.responseCode == 307 || e.responseCode == 308)
                                && manualRedirectCount++ < MAX_MANUAL_REDIRECTS;
                String redirectUrl = manuallyRedirect ? getRedirectUrl(e) : null;
                if (redirectUrl == null) {
                    try {
                        throw e;
                    } catch (HttpDataSource.InvalidResponseCodeException e1) {
                        e1.printStackTrace();
                    }
                }
                url = redirectUrl;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Util.closeQuietly(inputStream);
            }
        }

        //return null;
    }

    private static @Nullable
    String getRedirectUrl(HttpDataSource.InvalidResponseCodeException exception) {
        Map<String, List<String>> headerFields = exception.headerFields;
        if (headerFields != null) {
            List<String> locationHeaders = headerFields.get("Location");
            if (locationHeaders != null && !locationHeaders.isEmpty()) {
                return locationHeaders.get(0);
            }
        }
        return null;
    }

    private static List<DrmInitData.SchemeData> getSchemeDatas(
            DrmInitData drmInitData, UUID uuid, boolean allowMissingData) {
        // Look for matching scheme data (matching the Common PSSH box for ClearKey).
        List<DrmInitData.SchemeData> matchingSchemeDatas = new ArrayList<>(drmInitData.schemeDataCount);
        for (int i = 0; i < drmInitData.schemeDataCount; i++) {
            DrmInitData.SchemeData schemeData = drmInitData.get(i);
            boolean uuidMatches = schemeData.matches(uuid)
                    || (C.CLEARKEY_UUID.equals(uuid) && schemeData.matches(C.COMMON_PSSH_UUID));
            if (uuidMatches && (schemeData.data != null || allowMissingData)) {
                matchingSchemeDatas.add(schemeData);
            }
        }
        return matchingSchemeDatas;
    }

    private static DrmInitData.SchemeData getSchemeData(UUID uuid, List<DrmInitData.SchemeData> schemeDatas) {
        if (!C.WIDEVINE_UUID.equals(uuid)) {
            // For non-Widevine CDMs always use the first scheme data.
            return schemeDatas.get(0);
        }

        if (Util.SDK_INT >= 28 && schemeDatas.size() > 1) {
            // For API level 28 and above, concatenate multiple PSSH scheme datas if possible.
            DrmInitData.SchemeData firstSchemeData = schemeDatas.get(0);
            int concatenatedDataLength = 0;
            boolean canConcatenateData = true;
            for (int i = 0; i < schemeDatas.size(); i++) {
                DrmInitData.SchemeData schemeData = schemeDatas.get(i);
                byte[] schemeDataData = Util.castNonNull(schemeData.data);
                if (schemeData.requiresSecureDecryption == firstSchemeData.requiresSecureDecryption
                        && Util.areEqual(schemeData.mimeType, firstSchemeData.mimeType)
                        && Util.areEqual(schemeData.licenseServerUrl, firstSchemeData.licenseServerUrl)
                        && PsshAtomUtil.isPsshAtom(schemeDataData)) {
                    concatenatedDataLength += schemeDataData.length;
                } else {
                    canConcatenateData = false;
                    break;
                }
            }
            if (canConcatenateData) {
                byte[] concatenatedData = new byte[concatenatedDataLength];
                int concatenatedDataPosition = 0;
                for (int i = 0; i < schemeDatas.size(); i++) {
                    DrmInitData.SchemeData schemeData = schemeDatas.get(i);
                    byte[] schemeDataData = Util.castNonNull(schemeData.data);
                    int schemeDataLength = schemeDataData.length;
                    System.arraycopy(
                            schemeDataData, 0, concatenatedData, concatenatedDataPosition, schemeDataLength);
                    concatenatedDataPosition += schemeDataLength;
                }
                return firstSchemeData.copyWithData(concatenatedData);
            }
        }

        // For API levels 23 - 27, prefer the first V1 PSSH box. For API levels 22 and earlier, prefer
        // the first V0 box.
        for (int i = 0; i < schemeDatas.size(); i++) {
            DrmInitData.SchemeData schemeData = schemeDatas.get(i);
            int version = PsshAtomUtil.parseVersion(Util.castNonNull(schemeData.data));
            if (Util.SDK_INT < 23 && version == 0) {
                return schemeData;
            } else if (Util.SDK_INT >= 23 && version == 1) {
                return schemeData;
            }
        }

        // If all else fails, use the first scheme data.
        return schemeDatas.get(0);
    }

    private static byte[] adjustRequestInitData(UUID uuid, byte[] initData) {
        // TODO: Add API level check once [Internal ref: b/112142048] is fixed.
        if (C.PLAYREADY_UUID.equals(uuid)) {
            byte[] schemeSpecificData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
            if (schemeSpecificData == null) {
                // The init data is not contained in a pssh box.
                schemeSpecificData = initData;
            }
            initData =
                    PsshAtomUtil.buildPsshAtom(
                            C.PLAYREADY_UUID, addLaUrlAttributeIfMissing(schemeSpecificData));
        }

        // Prior to L the Widevine CDM required data to be extracted from the PSSH atom. Some Amazon
        // devices also required data to be extracted from the PSSH atom for PlayReady.
        if ((Util.SDK_INT < 21 && C.WIDEVINE_UUID.equals(uuid))
                || (C.PLAYREADY_UUID.equals(uuid)
                && "Amazon".equals(Util.MANUFACTURER)
                && ("AFTB".equals(Util.MODEL) // Fire TV Gen 1
                || "AFTS".equals(Util.MODEL) // Fire TV Gen 2
                || "AFTM".equals(Util.MODEL)))) { // Fire TV Stick Gen 1
            byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(initData, uuid);
            if (psshData != null) {
                // Extraction succeeded, so return the extracted data.
                return psshData;
            }
        }
        return initData;
    }

    private static byte[] addLaUrlAttributeIfMissing(byte[] data) {
        ParsableByteArray byteArray = new ParsableByteArray(data);
        // See https://docs.microsoft.com/en-us/playready/specifications/specifications for more
        // information about the init data format.
        int length = byteArray.readLittleEndianInt();
        int objectRecordCount = byteArray.readLittleEndianShort();
        int recordType = byteArray.readLittleEndianShort();
        if (objectRecordCount != 1 || recordType != 1) {
            Log.i(TAG, "Unexpected record count or type. Skipping LA_URL workaround.");
            return data;
        }
        int recordLength = byteArray.readLittleEndianShort();
        String xml = byteArray.readString(recordLength, Charset.forName(C.UTF16LE_NAME));
        if (xml.contains("<LA_URL>")) {
            // LA_URL already present. Do nothing.
            return data;
        }
        // This PlayReady object record does not include an LA_URL. We add a mock value for it.
        int endOfDataTagIndex = xml.indexOf("</DATA>");
        if (endOfDataTagIndex == -1) {
            Log.w(TAG, "Could not find the </DATA> tag. Skipping LA_URL workaround.");
        }
        String xmlWithMockLaUrl =
                xml.substring(/* beginIndex= */ 0, /* endIndex= */ endOfDataTagIndex)
                        + MOCK_LA_URL
                        + xml.substring(/* beginIndex= */ endOfDataTagIndex);
        int extraBytes = MOCK_LA_URL.length() * UTF_16_BYTES_PER_CHARACTER;
        ByteBuffer newData = ByteBuffer.allocate(length + extraBytes);
        newData.order(ByteOrder.LITTLE_ENDIAN);
        newData.putInt(length + extraBytes);
        newData.putShort((short) objectRecordCount);
        newData.putShort((short) recordType);
        newData.putShort((short) (xmlWithMockLaUrl.length() * UTF_16_BYTES_PER_CHARACTER));
        newData.put(xmlWithMockLaUrl.getBytes(Charset.forName(C.UTF16LE_NAME)));
        return newData.array();
    }

    private static String adjustRequestMimeType(UUID uuid, String mimeType) {
        // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
        if (Util.SDK_INT < 26
                && C.CLEARKEY_UUID.equals(uuid)
                && (MimeTypes.VIDEO_MP4.equals(mimeType) || MimeTypes.AUDIO_MP4.equals(mimeType))) {
            return CENC_SCHEME_MIME_TYPE;
        }
        return mimeType;
    }
}
