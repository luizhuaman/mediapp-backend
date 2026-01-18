package com.telefonica.pcr.client.impl;

import com.telefonica.pcr.client.INotiMessageClient;
import com.telefonica.pcr.commons.StreamProperties;
import com.telefonica.pcr.external.request.NotiMessageRequest;
import com.telefonica.pcr.external.response.NotiMessageResponse;
import com.telefonica.pcr.proxy.RestTemplateHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.telefonica.pcr.commons.Constant.GENERIC_ERROR_CODE;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotiMessageClient implements INotiMessageClient {
    private final StreamProperties streamProperties;
    private final RestTemplateHelper restTemplateHelper;

    @Override
    public NotiMessageResponse sendRequest(NotiMessageRequest request, boolean isOnlineMessaging) {
        NotiMessageResponse response;
        String url = isOnlineMessaging ? streamProperties.getServiceWhatsAppSend() : streamProperties.getServiceWhatsAppSave();
        try {
            response = restTemplateHelper.requestPost(url, request,
                    NotiMessageResponse.class);
        } catch (Exception e) {
            response = NotiMessageResponse.builder()
                    .trackingId(request.getTrackingId())
                    .statusCode(GENERIC_ERROR_CODE)
                    .statusDesc(e.getMessage())
                    .successful(Boolean.FALSE)
                    .build();
        }

        return response;
    }
}
