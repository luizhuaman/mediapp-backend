package com.telefonica.pcr.business;

import static com.telefonica.pcr.commons.Constant.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.telefonica.pcr.commons.StreamProperties;
import com.telefonica.pcr.commons.Utils;
import com.telefonica.pcr.dto.CallRetentionDTO;
import com.telefonica.pcr.entity.redis.BlackListPortabilityQuery;
import com.telefonica.pcr.entity.redis.Contactability;
import com.telefonica.pcr.redis.RedisEntities;

import lombok.RequiredArgsConstructor;

/**
 * Contiene informacion de los filtros realizados para la Campaña
 *
 * @author: Pablo Fernando Guzman Quispe, Telefonica Company
 * @version: 0.1 - Released in July, 2021
 */
@Service
@RequiredArgsConstructor
public class Filters {
    private final StreamProperties properties;
    private final RedisEntities    entities;

    public boolean isCorrectData(CallRetentionDTO dto) {
        return this.isThePhoneNumber(dto) && this.isTrafficTypeEmpty(dto) && this.isNotDurationEmpty(dto)
                && this.isNotTheTrafficDateEmpty(dto) && this.isNotTheExternalOperatorEmpty(dto);
    }

    public boolean isThePhoneNumber(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getPhoneNumber());
    }

    public boolean isTrafficTypeEmpty(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getCallRivalsRetention().getTrafficType());
    }

    public boolean isNotDurationEmpty(CallRetentionDTO dto) {
        if (!dto.getCallRivalsRetention().getTrafficType().equalsIgnoreCase(WEB))
            return StringUtils.isNotBlank(dto.getCallRivalsRetention().getDuration());
        return true;
    }

    public boolean isNotTheTrafficDateEmpty(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getCallRivalsRetention().getTrafficDate());
    }

    public boolean isNotTheExternalOperatorEmpty(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getCallRivalsRetention().getExternalOperator());
    }

    /**
     * isInWhiteListFilter
     *
     * @param dto RechargeDTO phoneNumber
     * @return valida la lista blanca
     */
    public boolean isInTheWhiteList(CallRetentionDTO dto) {
        if (properties.isWhiteListFlagOn())
            return entities.isInWhiteList(dto.getPhoneNumber());
        return true;
    }

    public boolean isNotWebTraffic(CallRetentionDTO dto) {
        return !dto.getCallRivalsRetention().getTrafficType().equalsIgnoreCase(WEB);
    }

    public boolean isNotFixedTraffic(CallRetentionDTO dto) {
        return !dto.getCallRivalsRetention().getTrafficType().equalsIgnoreCase(FIXED);
    }

    public boolean isLastHoursTraffic(CallRetentionDTO dto) {
        return Utils.theTrafficDateIsNotLessThanFourHours(dto.getCallRivalsRetention().getTrafficDate());
    }

    public boolean isAllowedExternalOperator(CallRetentionDTO dto) {
        String externalOperator = dto.getCallRivalsRetention().getExternalOperator();
        return entities.isInTheListOfAllowedOperators(externalOperator.toUpperCase());
    }

    public boolean isThresholdParamPresent(CallRetentionDTO dto) {
        return ObjectUtils.isNotEmpty(dto.getThreshold());
    }

    /**
     * Si la línea del cliente tiene un tipo de transacción de consulta de portabilidad(CP), el evento fluye. Si la
     * línea del cliente tiene un tipo de transacción de solicitud de portabilidad(SP) con error, el evento fluye.
     *
     * @param dto CallRetentionDTO
     * @return valida si la línea esta en la lista negra de portabilidad.
     */
    public boolean hasNotCP(CallRetentionDTO dto) {
        Optional<BlackListPortabilityQuery> inBlackListCPABD = entities.findInBlackListCPABD(dto.getPhoneNumber());
        if (inBlackListCPABD.isPresent()) {
            long hours = inBlackListCPABD.get().getEventDate().until(LocalDateTime.now(), ChronoUnit.HOURS);
            return hours > (dto.getThreshold().getRangeCPDays() * HOURS);
        }
        return true;
    }

    /**
     * Si el cliente ha sido atendido por otra campaña se filtra.
     *
     * @param dto CallRetentionDTO
     * @return valida si existe en token portability
     */
    public boolean wasNotApproachedByTR(CallRetentionDTO dto) {
        return !entities.findInContactabilityTokenRetention(dto.getPhoneNumber()).isPresent();
    }

    /**
     * Si la duración de la llamado o navegación en servicios de la competencia no se encuentra entre los intervalos de
     * 2 a 15 min, el evento se filtra.
     *
     * @param dto CallRetentionDTO
     * @return valida si la duración del evento esta en el rango
     */
    public boolean isTheDurationInTheRange(CallRetentionDTO dto) {
        if (!dto.getCallRivalsRetention().getTrafficType().equals(WEB)) {
            return durationRuleByDirectionOfTraffic(dto);
        }
        return true;
    }

    /**
     * Valida si el evento recibido el trafico sea movil o web.
     *
     * @param dto objeto intermedio
     * @return boolean resultado de la validacion
     */
    public boolean isMobileOrWeb(CallRetentionDTO dto) {
        return Arrays.asList(MOBILE, WEB).contains(dto.getCallRivalsRetention().getTrafficType());
    }

    private boolean durationRuleByDirectionOfTraffic(CallRetentionDTO dto) {
        if (dto.getCallRivalsRetention().getTrafficDirection().equalsIgnoreCase(IN)) {
            int time = Utils.tryParseInt(dto.getCallRivalsRetention().getDuration());
            return time > (dto.getThreshold().getMinQuantityOfCallDuration() * SECONDS);
        }
        return true;
    }

    /**
     * Valida si el evento recibido subscribers sea diferente de null.
     *
     * @param dto objeto intermedio
     * @return boolean resultado de la validacion
     */
    public boolean isNotSubscriberEmpty(CallRetentionDTO dto) {
        return Objects.nonNull(dto.getSubscribers());
    }

    /**
     * Valida si el evento recibido subscribers contiene el documento.
     *
     * @param dto objeto intermedio
     * @return boolean resultado de la validacion
     */
    public boolean isNotDocumentEmpty(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getSubscribers().getDocumentNumber())
                && StringUtils.isNotBlank(dto.getSubscribers().getDocumentType());
    }

    /**
     * Evitamos saturar al usuario de mensajes.
     *
     * @param dto CallRetentionDTO
     * @return valida si existe en el hash de contactability
     */
    public boolean isNotInContactability(CallRetentionDTO dto) {
        Optional<Contactability> contactabilityOptional = entities
                .findInContactabilityCallRetention(dto.getSubscribers().getSubscriberCd());
        return !contactabilityOptional.isPresent();
    }

    /**
     * Valida si el evento recibido sea Postpago, Control o Caribu.
     *
     * @param dto objeto intermedio
     * @return boolean resultado de la validacion
     */
    public boolean isPostpaid(CallRetentionDTO dto) {
        return !dto.getSubscribers().getProductTypeDesc().equalsIgnoreCase(PREPAID);
    }

    /**
     * Valida si el evento recibido sea Residencial.
     *
     * @param dto objeto intermedio
     * @return boolean resultado de la validacion
     */
    public boolean isResidential(CallRetentionDTO dto) {
        if (ObjectUtils.isEmpty(dto.getSubscribers().getCustomerSegmentDesc())) {
            return (Arrays.asList(SEGMENT_RESIDENCIAL).contains(dto.getSubscribers().getDocumentType()) || (
                    RUC.equalsIgnoreCase(dto.getSubscribers().getDocumentType()) && dto.getSubscribers()
                            .getDocumentNumber()
                            .substring(0, 2)
                            .equalsIgnoreCase(DIEZ)));
        }
        return RESIDENCIAL.equalsIgnoreCase(dto.getSubscribers().getCustomerSegmentDesc());
    }

    /**
     * Valida que el codigo del plan comercial del cliente no sea plan Familia.
     *
     * @param dto objeto intermedio
     * @return boolean resultado de la validacion
     */
    public boolean hasNotFamilyPlan(CallRetentionDTO dto) {
        return !entities.isPlanInBlacklist(dto.getSubscribers().getCommercialPlanCd());
    }

    public boolean isClusterGroupVariablesPresent(CallRetentionDTO dto) {
        return ObjectUtils.isNotEmpty(dto.getClusterGroupVariableList());
    }

    public boolean isClusterGroupTagPresent(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getClusterGroupTag());
    }

    public boolean isProductTagPresent(CallRetentionDTO dto) {
        return ObjectUtils.isNotEmpty(dto.getTransversalGroupTag());
    }

    public boolean isDiscountTagPresent(CallRetentionDTO dto) {
        return ObjectUtils.isNotEmpty(dto.getDiscountTag());
    }

    public boolean isTargetClusterPresent(CallRetentionDTO dto) {
        return ObjectUtils.isNotEmpty(dto.getTargetClusterList());
    }

    public boolean isGroupTagPresent(CallRetentionDTO dto) {
        return StringUtils.isNotBlank(dto.getGroupTag());
    }

    public boolean isClusterOfferPresent(CallRetentionDTO dto) {
        return ObjectUtils.isNotEmpty(dto.getClusterOffer());
    }

    public boolean isPlanCatalogPresent(CallRetentionDTO dto) {
        return Objects.nonNull(dto.getPlanesCatalog());
    }

    public boolean isActiveSending(CallRetentionDTO dto) {
        if (ObjectUtils.isEmpty(dto.getActivateSending())) return false;
        return ACTIVATE_SENDING.equalsIgnoreCase(dto.getActivateSending().getActiveWhatsAppSending()) && dto.getThreshold().isActiveSendingWhatsApp();
    }

    public boolean isCustomerNotiConfigPresent(CallRetentionDTO dto) {
        return Objects.nonNull(dto.getCustomerNotiConfig());
    }

    public boolean isCustomerNotiTemplatePresent(CallRetentionDTO dto) {
        return Objects.nonNull(dto.getTemplateWspBody());
    }

    public boolean isSatisfactoryResponse(CallRetentionDTO dto) {
        return ObjectUtils.isEmpty(dto.getNotifyPartyEventResponse().getCode());
    }
}
