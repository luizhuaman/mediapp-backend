package com.telefonica.pcr.service;

import com.telefonica.pcr.commons.Utils;
import com.telefonica.pcr.dto.CallRetentionDTO;
import com.telefonica.pcr.external.request.NotiMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.telefonica.pcr.commons.Constant.*;

/**
 * Clase con operaciones para NotificationMessage
 *
 * @author Indra Company
 * @version 0.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMessageService {
    public NotiMessageRequest buildCustomerNotiRequest(CallRetentionDTO dto) {
        NotiMessageRequest request = new NotiMessageRequest();
        request.setTrackingId(dto.getStreamTrackingId());
        request.setCampaignId(CAMPAIGN_ID);
        request.setSubject(WHATSAPP_SUBJECT);
        request.setDescription(WHATSAPP_DESCRIPTION);
        request.setNotificationType(WHATSAPP_NOTIFICATION_TYPE);
        request.setTemplateId(dto.getTemplateWspBody().getTemplateId());
        request.setReceiverId(dto.getSubscribers().getDocumentNumber());
        request.setReceiverName(dto.getSubscribers().getCustomerName());
        request.setSubscriberId(dto.getSubscribers().getSubscriberCd());
        request.setReceiverPhoneNumber(dto.getPhoneNumber());
        request.setSenderId(dto.getCustomerNotiConfig().getSenderId());
        request.setSenderName(dto.getCustomerNotiConfig().getSenderName());
        request.setSenderPhoneNumber(dto.getCustomerNotiConfig().getSenderPhone());
        request.setParams(getCustomerNotiParameters(dto));
        return request;
    }

    private Map<String, String> getCustomerNotiParameters(CallRetentionDTO dto) {
        Map<String, String> map = new HashMap<>();
        dto.getTemplateWspBody().getVariables().forEach((k, v) -> {
            String fieldValue = Utils.extractAttributeFromDTO(dto, v);
            if (Objects.nonNull(fieldValue)) {
                if (dto.getTemplateWspBody().isContainsStaticValues() && EMPTY.equals(fieldValue)) {
                    map.put(k, v);
                } else {
                    map.put(k, fieldValue);
                }
            }
        });
        return map;
    }
}
