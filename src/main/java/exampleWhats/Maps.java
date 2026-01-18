package com.telefonica.pcr.business;

import static com.telefonica.pcr.commons.Constant.*;
import static com.telefonica.pcr.commons.Utils.buildCustomTracking;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.telefonica.pcr.client.impl.NotiMessageClient;
import com.telefonica.pcr.external.request.NotiMessageRequest;
import com.telefonica.pcr.service.NotificationMessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.telefonica.pcr.client.impl.NotifyPartyEventsClient;
import com.telefonica.pcr.client.impl.SatPushClient;
import com.telefonica.pcr.commons.ThreadLog;
import com.telefonica.pcr.commons.TriPredicate;
import com.telefonica.pcr.commons.Utils;
import com.telefonica.pcr.dto.CallRetentionDTO;
import com.telefonica.pcr.entity.redis.*;
import com.telefonica.pcr.enums.DiscountEnum;
import com.telefonica.pcr.enums.TransversalGroupEnum;
import com.telefonica.pcr.external.request.NotifyPartyEventRequest;
import com.telefonica.pcr.external.request.SatPushRequest;
import com.telefonica.pcr.external.response.NotifyPartyEventResponse;
import com.telefonica.pcr.pojo.CallRivalsRetention;
import com.telefonica.pcr.redis.RedisEntities;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class Maps {

    private final RedisEntities              redisEntities;
    private final CampsysBusiness            campsysBusiness;
    private final SatPushClient              satPushClient;
    private final NotifyPartyEventsClient    notifyPartyEventsClient;
    private final NotiMessageClient          notiMessageClient;
    private final NotificationMessageService notificationMessageService;

    public CallRetentionDTO fromCallRivalsRetentionToDto(CallRivalsRetention callrivalsRetention) {
        CallRetentionDTO dto = new CallRetentionDTO();
        dto.setStreamTrackingId(ThreadLog.getTrackingId());
        dto.setEventId(callrivalsRetention.getEventId());
        dto.setPhoneNumber(standardizePhoneNumber(callrivalsRetention.getServiceId()));
        dto.setCallRivalsRetention(callrivalsRetention);
        return dto;
    }

    public CallRetentionDTO setThresholdParams(CallRetentionDTO dto) {
        redisEntities.findThreshold(CAMPAIGN_ID).ifPresent(dto::setThreshold);
        return dto;
    }

    private String standardizePhoneNumber(String phoneNumber) {
        if (!ObjectUtils.isEmpty(phoneNumber) && phoneNumber.startsWith(COUNTRY_CODE)) {
            return phoneNumber.substring(2);
        }
        return phoneNumber;
    }

    public CallRetentionDTO setSubscriberInfo(CallRetentionDTO dto) {
        redisEntities.findInSubscribers(dto.getPhoneNumber()).ifPresent(subscribers -> {
            dto.setSubscribers(subscribers);
            dto.setCustomerName(subscribers.getCustomerName().split(SPACE)[0]);
        });
        return dto;
    }

    public CallRetentionDTO setClusterGroupVariables(CallRetentionDTO dto) {
        String sector = StringUtils.isBlank(dto.getSubscribers().getDecilPropenPortout()) ? EMPTY_SECTOR
                : dto.getSubscribers().getDecilPropenPortout();

        List<ClusterGroupVariable> clusterGroupVariableList = redisEntities.findClusterGroupVariable(
                String.format("%s:%s", dto.getCallRivalsRetention().getTrafficDirection().toUpperCase(), sector));

        dto.setClusterGroupVariableList(clusterGroupVariableList);

        return dto;
    }

    public CallRetentionDTO setClusterGroupTag(CallRetentionDTO dto) {
        Optional<ClusterGroupVariable> optionalClusterTraffic = dto.getClusterGroupVariableList()
                .stream()
                .filter(p -> isDurationInRange(dto, p))
                .filter(p -> isOperatorValid(dto, p))
                .max(Comparator.comparing(c -> ObjectUtils.isNotEmpty(c.getOperatorName()) ? 1 : 0));

        optionalClusterTraffic.ifPresent(p -> dto.setClusterGroupTag(p.getClusterGroupTag()));

        return dto;
    }

    private static boolean isDurationInRange(CallRetentionDTO dto, ClusterGroupVariable entity) {
        int intervalStartValueInSecond = entity.getDurationIntervalStartValue() * SECONDS;
        int intervalEndValueInSecond = entity.getDurationIntervalEndValue() * SECONDS;
        int duration = Utils.tryParseInt(dto.getCallRivalsRetention().getDuration());
        return isInRangeForInteger.test(intervalStartValueInSecond, intervalEndValueInSecond, duration);
    }

    private static boolean isOperatorValid(CallRetentionDTO dto, ClusterGroupVariable entity) {
        return ObjectUtils.isEmpty(entity.getOperatorName()) || dto.getCallRivalsRetention().getExternalOperator().equalsIgnoreCase(entity.getOperatorName());
    }

    public CallRetentionDTO setTransversalGroupTag(CallRetentionDTO dto) {
        if (hasMovistarTotalFlag(dto)) {
            dto.setTransversalGroupTag(TransversalGroupEnum.MOVISTAR_TOTAL);
            return dto;
        }

        if (hasFixedFlag(dto)) {
            dto.setTransversalGroupTag(TransversalGroupEnum.MOBILE_WITH_FIXED);
            return dto;
        }

        if (hasMultipleLinesFlag(dto)) {
            dto.setTransversalGroupTag(TransversalGroupEnum.MULTIPLE_LINES);
            return dto;
        }

        dto.setTransversalGroupTag(TransversalGroupEnum.REST);

        return dto;
    }

    private boolean hasMovistarTotalFlag(CallRetentionDTO dto) {
        return StringUtils.equals(ONE, dto.getSubscribers().getFlagmt());
    }

    private boolean hasFixedFlag(CallRetentionDTO dto) {
        Set<String> fixedPlanks = findInFixedPlankByDocumentNumber(dto);
        return ObjectUtils.isNotEmpty(fixedPlanks);
    }

    private boolean hasMultipleLinesFlag(CallRetentionDTO dto) {
        Set<String> mobilePlanks = findInMobilePlankByDocumentNumber(dto);
        return mobilePlanks.size() > 1;
    }

    private Set<String> findInFixedPlankByDocumentNumber(CallRetentionDTO dto) {
        String key = String.format("%s_%s", dto.getSubscribers().getDocumentType(),
                dto.getSubscribers().getDocumentNumber());

        return redisEntities.getFixedServices(key)
                .stream()
                .filter(o -> ObjectUtils.notEqual(o.getFlagMtInd(), FLAG_MT))
                .map(FixedPlank::getFinancialAccount)
                .collect(Collectors.toSet());
    }

    private Set<String> findInMobilePlankByDocumentNumber(CallRetentionDTO dto) {
        Optional<MobilePlankRelation> inDocumentSubscribers = redisEntities
                .findMobilePlankRelation(dto.getSubscribers().getDocumentNumber());

        return inDocumentSubscribers.map(o -> Utils.splitBy(o.getPhoneNumbers(), COMMA)).orElse(Collections.emptySet());
    }

    public CallRetentionDTO setDiscountTag(CallRetentionDTO dto) {
        dto.setDiscountTag(DiscountEnum.findKey(dto));
        return dto;
    }

    public CallRetentionDTO setTargetCluster(CallRetentionDTO dto) {
        List<TargetCluster> targetClusterList = redisEntities
                .findTargetCluster(String.format("%s:%s:%s", dto.getClusterGroupTag().toUpperCase(),
                        dto.getTransversalGroupTag().getKey(), dto.getDiscountTag().getKey()));

        dto.setTargetClusterList(targetClusterList);

        return dto;
    }

    public CallRetentionDTO setGroupTag(CallRetentionDTO dto) {
        Optional<TargetCluster> targetClusterOptional = dto.getTargetClusterList()
                .stream()
                .filter(o -> isCustomerSeniorityInRange(dto, o))
                .filter(o -> isNetFixedChargeInRange(dto, o.getNetFixedChargeIntervalStartValue(),
                        o.getNetFixedChargeIntervalEndValue()))
                .findFirst();

        targetClusterOptional.ifPresent(o -> dto.setGroupTag(o.getGroupTag()));
        return dto;
    }

    private boolean isNetFixedChargeInRange(CallRetentionDTO dto, Double startInterval, Double endInterval) {
        Double netFixedCharge = Utils.tryParseDouble(dto.getSubscribers().getNetFixedCharge());
        return isInRangeForDouble.test(startInterval, endInterval, netFixedCharge);
    }

    private boolean isCustomerSeniorityInRange(CallRetentionDTO dto, TargetCluster entity) {
        if (ObjectUtils.notEqual(dto.getTransversalGroupTag(), TransversalGroupEnum.REST))
            return true;

        int oldLine = Utils.tryParseInt(dto.getSubscribers().getOldLine());

        return isInRangeForInteger.test(entity.getCustomerSeniorityIntervalStartValue() * DAYS,
                entity.getCustomerSeniorityIntervalEndValue() * DAYS, oldLine);
    }

    public CallRetentionDTO setClusterOffer(CallRetentionDTO dto) {
        redisEntities.findClusterOffer(dto.getGroupTag().toUpperCase())
                .ifPresent(dto::setClusterOffer);
        return dto;
    }

    public CallRetentionDTO setPlanesCatalog(CallRetentionDTO dto) {
        redisEntities.findInPlanesCatalog(dto.getSubscribers().getCommercialPlanCd()).ifPresent(dto::setPlanesCatalog);
        return dto;
    }

    public CallRetentionDTO assignFixedCharge(CallRetentionDTO dto) {
        if (StringUtils.isBlank(dto.getPlanesCatalog().getChargeCodeOfPlan())) {
            dto.setFixedRecharge(MIN_MOUNT);
        } else {
            dto.setFixedRecharge(dto.getPlanesCatalog().getChargeCodeOfPlan());
        }
        return dto;
    }

    public CallRetentionDTO setCalculator(CallRetentionDTO dto) {
        redisEntities.findCalculator(dto.getPhoneNumber()).ifPresent(dto::setCalculator);
        return dto;
    }

    public CallRetentionDTO setSegment(CallRetentionDTO dto) {
        Calculator calculator = dto.getCalculator();

        if (ObjectUtils.isEmpty(calculator) || StringUtils.isEmpty(calculator.getSegment())
                || !POSTPAID.equalsIgnoreCase(calculator.getMobilProduct())) {
            dto.setSegment(StringUtils.EMPTY);
            return dto;
        }

        dto.setSegment(calculator.getSegment());

        return dto;
    }

    public CallRetentionDTO setActivateSending(CallRetentionDTO dto) {
        redisEntities.findInActivateSendingByCluster(dto.getClusterGroupTag().toUpperCase())
                .flatMap(activations -> Arrays.stream(activations)
                        .filter(activation -> isEligible(dto, activation))
                        .findFirst()
                ).ifPresent(dto::setActivateSending);

        return dto;
    }

    private boolean isEligible(CallRetentionDTO dto, ActivateSending activation) {
        return isAntiquityInRange(dto, activation) && isFixedChargeInRange(dto, activation);
    }

    private boolean isAntiquityInRange(CallRetentionDTO dto, ActivateSending activation) {
        int oldLine = Utils.tryParseInt(dto.getSubscribers().getOldLine());
        return oldLine >= activation.getMinAntiquityLine() && oldLine < activation.getMaxAntiquityLine();
    }

    private boolean isFixedChargeInRange(CallRetentionDTO dto, ActivateSending activation) {
        if (FIXED_CHARGE_NET.equalsIgnoreCase(activation.getTypeFixedCharge())) {
            Double netFixedCharge = Utils.tryParseDouble(dto.getSubscribers().getNetFixedCharge());
            return netFixedCharge >= activation.getMinFixedCharge() && netFixedCharge <= activation.getMaxFixedCharge();
        }
        Double fixedCharge = Utils.tryParseDouble(dto.getSubscribers().getFixedCharge());
        return fixedCharge >= activation.getMinFixedCharge() && fixedCharge <= activation.getMaxFixedCharge();
    }

    public void saveCustomerContact(CallRetentionDTO dto) {
        campsysBusiness.insertContact(dto);
    }

    public void sendSatPush(CallRetentionDTO dto) {
        SatPushRequest request = buildSatPushRequest(dto);
        satPushClient.sendRequest(request);
    }

    private SatPushRequest buildSatPushRequest(CallRetentionDTO dto) {
        return SatPushRequest.builder()
                .trackingId(dto.getStreamTrackingId())
                .phoneNumber(dto.getPhoneNumber())
                .subscriberId(dto.getSubscribers().getSubscriberCd())
                .campaignId(CAMPAIGN_ID)
                .messageSender(MESSAGESENDER)
                .messageType(MESSAGETYPE)
                .message1(dto.getClusterOffer().getMessage())
                .offerCode(OFERTAINFORMATIVA_CODE)
                .offerId(OFERTAINFORMATIVA_ID)
                .textCallDestination(String.format(TEXT_CALL_FORMAT, dto.getClusterOffer().getCallDestination()))
                .callDestination(dto.getClusterOffer().getCallDestination())
                .build();
    }

    public CallRetentionDTO sendLeadToThirdParty(CallRetentionDTO dto) {
        HttpHeaders headers = buildThirdPartyHeader(dto);
        NotifyPartyEventRequest body = buildThirdPartyBody(dto);
        dto.setTransactionDate(LocalDateTime.now());

        NotifyPartyEventResponse response = notifyPartyEventsClient.sendRequest(headers, body);
        dto.setNotifyPartyEventRequest(body);
        dto.setNotifyPartyEventResponse(response);
        return dto;
    }

    private HttpHeaders buildThirdPartyHeader(CallRetentionDTO dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(UNICA_USER_KEY, UNICA_USER_VALUE);
        headers.set(UNICA_SERVICEID_KEY, UNICA_SERVICEID_VALUE);
        headers.set(UNICA_APPLICATION_KEY, UNICA_APPLICATION_VALUE);
        headers.set(UNICA_PID_KEY, buildCustomTracking(dto.getStreamTrackingId()));
        return headers;
    }

    private NotifyPartyEventRequest buildThirdPartyBody(CallRetentionDTO dto) {
        return NotifyPartyEventRequest.builder()
                .fullName(dto.getSubscribers().getCustomerName())
                .telephone(dto.getPhoneNumber())
                .fixedCharge(dto.getFixedRecharge())
                .product(buildProduct(dto))
                .documentNumber(dto.getSubscribers().getDocumentNumber())
                .mobileOffer(MOBILE_OFFER)
                .suggestedEquipment(Arrays.asList(dto.getCallRivalsRetention().getExternalOperator(), buildOffers(dto)))
                .subscriptionValue(dto.getSegment())
                .build();
    }

    private String buildProduct(CallRetentionDTO dto) {
        return String.format(SUGGESTION_WRAPPER, dto.getTransversalGroupTag().getValue(), PIPE,
                dto.getClusterGroupTag());
    }

    private String buildOffers(CallRetentionDTO dto) {
        return String.format(SUGGESTION_WRAPPER, dto.getClusterOffer().getFirstOffer(), PIPE,
                dto.getClusterOffer().getSecondOffer());
    }

    public void saveLeadInfo(CallRetentionDTO dto) {
        campsysBusiness.insertLeadInfo(dto);
    }

    public void saveContactability(CallRetentionDTO dto) {
        Contactability contactability = new Contactability();
        contactability.setSubscriberCd(dto.getSubscribers().getSubscriberCd());
        contactability.setPhoneNumber(dto.getPhoneNumber());
        contactability.setNumContactos(ONE);
        contactability.setExpiration(
                Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(dto.getThreshold().getContactability()));
        redisEntities.saveInContactability(dto.getSubscribers().getSubscriberCd(), contactability);
    }

    private static final TriPredicate<Integer> isInRangeForInteger = (startInterval, endInterval,
                                                                      value) -> startInterval <= value && value < endInterval;

    private static final TriPredicate<Double> isInRangeForDouble = (startInterval, endInterval,
                                                                    value) -> startInterval <= value && value < endInterval;

    public CallRetentionDTO setCustomerNotiConfig(CallRetentionDTO dto) {
        redisEntities.findCustomerNotiConfig(KEY_NOTI_CONFIG).ifPresent(dto::setCustomerNotiConfig);
        return dto;
    }

    public CallRetentionDTO setCustomerNotiTemplate(CallRetentionDTO dto) {
        redisEntities.findTemplateByOfferType(dto.getClusterOffer().getOfferType()).ifPresent(dto::setTemplateWspBody);
        return dto;
    }

    public void sendWhatsAppNotification(CallRetentionDTO dto) {
        NotiMessageRequest request = notificationMessageService.buildCustomerNotiRequest(dto);
        notiMessageClient.sendRequest(request, dto.getCustomerNotiConfig().isOnlineMessaging());
    }
}
