package com.tdp.ms.caplupsellsale.business;

import static com.tdp.ms.caplupsellsale.commons.Constant.*;
import static com.tdp.ms.caplupsellsale.commons.Utils.*;
import static org.apache.commons.lang3.StringUtils.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.tdp.ms.caplupsellsale.entity.redis.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import com.tdp.ms.caplupsellsale.service.SendingFeedbackService;
import com.tdp.ms.caplupsellsale.commons.Constant;
import com.tdp.ms.caplupsellsale.commons.ThreadLog;
import com.tdp.ms.caplupsellsale.commons.Utils;
import com.tdp.ms.caplupsellsale.dto.CaplUpsellSaleDTO;
import com.tdp.ms.caplupsellsale.entity.campsys.CaplUpsellSale;
import com.tdp.ms.caplupsellsale.pojo.*;
import com.tdp.ms.caplupsellsale.redis.RedisEntities;

import lombok.RequiredArgsConstructor;

/**
 * Contiene información de las transformaciones realizadas.
 *
 * @author: Elizabeth Valdez, Indra Company
 * @version 0.1 - Released on December 01, 2023
 */
@Service
@RequiredArgsConstructor @Slf4j
public class Maps {

    private final RedisEntities   entities;
    private final CampsysBusiness campsysBusiness;
    private final SendingFeedbackService sendingFeedbackService;

    /**
     * Crea un objeto CaplUpsellSaleDTO a partir de NotifyExternalSupRequest.
     *
     * @param notifyExternalSupRequest el objeto NotifyExternalSupRequest
     * @return el objeto CaplUpsellSaleDTO creado
     */
    public CaplUpsellSaleDTO fromNotifyExternalSupToDTO(NotifyExternalSupRequest notifyExternalSupRequest) {
        CaplUpsellSaleDTO dto = new CaplUpsellSaleDTO();
        dto.setStreamTrackingId(ThreadLog.getTrackingId());
        dto.setEventId(notifyExternalSupRequest.getTrackingId());
        dto.setOperationCode(Optional.of(notifyExternalSupRequest.getOperationCode().toUpperCase()).orElse(EMPTY));
        dto.setPhoneNumber(Optional.ofNullable(notifyExternalSupRequest.getPhoneNumber()).orElse(EMPTY));
        dto.setBoltonCode(notifyExternalSupRequest.getBonoCode());
        dto.setNotifyExternalSupRequest(notifyExternalSupRequest);
        return dto;
    }

    /**
     * Establece los suscriptores por numero de celular en el objeto CaplUpsellSaleDTO mediante la búsqueda en
     * RedisEntities.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con los suscriptores establecidos si se encuentra en RedisEntities
     */
    public CaplUpsellSaleDTO setDtoSubscribers(CaplUpsellSaleDTO dto) {
        entities.findInSubscribersPhoneNumber(dto.getPhoneNumber()).ifPresent(s -> {
            dto.setSubscribers(s);
            dto.setSubscriberCd(s.getSubscriberCd());
        });
        return dto;
    }

    /**
     * Verifica si el suscriptor ha recibido un recibo en el mes 1.
     *
     * @param billingCycle El ciclo de facturación del suscriptor.
     * @return {@code true} si el suscriptor ha recibido un recibo en el mes 1, {@code false} de lo contrario.
     */
    public boolean hasReceiptM1(String billingCycle, LocalDate registrationDate, int monthsRegistration) {
        int billingCycleDay = NumberUtils.toInt(billingCycle, BILLING_CYCLE_FIVE);
        LocalDate billingCycleDate = registrationDate.plusMonths(Math.max(monthsRegistration, ONE_INT));

        int lastDayOfMonth = billingCycleDate.lengthOfMonth();

        if (billingCycleDay > lastDayOfMonth) {
            billingCycleDay = lastDayOfMonth;
        }

        billingCycleDate = billingCycleDate.withDayOfMonth(billingCycleDay);

        return billingCycleDate.isBefore(LocalDate.now());
    }

    /**
     * Establece la validación de las deudas del suscriptor en el objeto CaplUpsellSaleDTO.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información del suscriptor.
     * @return El objeto CaplUpsellSaleDTO con la validación de las deudas establecida.
     */
    public CaplUpsellSaleDTO setDtoValidationDebts(CaplUpsellSaleDTO dto) {
        LocalDate registrationDate = Utils.formatLocalDate(dto.getSubscribers().getMobileLineRegistrationDate(), DATE_FORMAT_2);
        int months = Period.between(Objects.requireNonNull(registrationDate), LocalDate.now()).getMonths();
        String debtStatus = dto.getSubscribers().getHasDebtM1() + dto.getSubscribers().getHasDebtM2() + dto.getSubscribers().getHasDebtM3();
        boolean hasReceiptM1 = hasReceiptM1(dto.getSubscribers().getBillingCycle(), registrationDate, months);

        if (months == 3) {
            if (hasReceiptM1) setDebtStatusForThreeMonths(dto, debtStatus);
            else setDebtStatusForTwoMonths(dto, debtStatus);
        } else if (months == 2) {
            if (hasReceiptM1) setDebtStatusForTwoMonths(dto, debtStatus);
            else setDebtStatusForOneMonth(dto, debtStatus);
        } else if (months == 1 && hasReceiptM1) {
            setDebtStatusForOneMonth(dto, debtStatus);
        } else if (hasReceiptM1) {
            setDebtStatusForOneMonth(dto, debtStatus);
        } else {
            dto.setHasPaidSomeReceipts(true);
        }
        return dto;
    }

    /**
     * Establece el estado de la deuda del suscriptor para tres meses en el objeto CaplUpsellSaleDTO.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información del suscriptor.
     * @param debtStatus El estado de la deuda del suscriptor para tres meses.
     */
    private void setDebtStatusForThreeMonths(CaplUpsellSaleDTO dto, String debtStatus) {
        switch (debtStatus) {
            case DEBT_M1_M2_M3:
                dto.setHasDebtAllMonths(true);
                break;
            case DEBT_M3:
                dto.setHasDebtM3(true);
                break;
            case DEBT_M2_M3:
                dto.setHasDebtM2M3(true);
                break;
            case DEBT_M2:
                dto.setHasDebtM2(true);
                break;
            default: dto.setHasPaidSomeReceipts(true);
                break;
        }
    }

    /**
     * Establece el estado de la deuda del suscriptor para dos meses en el objeto CaplUpsellSaleDTO.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información del suscriptor.
     * @param debtStatus El estado de la deuda del suscriptor para dos meses.
     */
    private void setDebtStatusForTwoMonths(CaplUpsellSaleDTO dto, String debtStatus) {
        switch (debtStatus) {
            case DEBT_M1_M2:
                dto.setHasDebtAllMonths(true);
                break;
            case DEBT_M2:
                dto.setHasDebtM2(true);
                break;
            default:
                dto.setHasPaidSomeReceipts(true);
                break;
        }
    }

    /**
     * Establece el estado de la deuda del suscriptor para un mes en el objeto CaplUpsellSaleDTO.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información del suscriptor.
     * @param debtStatus El estado de la deuda del suscriptor para un mes.
     */
    private void setDebtStatusForOneMonth(CaplUpsellSaleDTO dto, String debtStatus) {
        if (debtStatus.equals(DEBT_M1)) {
            dto.setHasDebtAllMonths(true);
        } else {
            dto.setHasPaidSomeReceipts(true);
        }
    }

    /**
     * Establece el catálogo de planes en el objeto CaplUpsellSaleDTO mediante la búsqueda en RedisEntities.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con el catálogo de planes establecido si se encuentra en RedisEntities
     */
    public CaplUpsellSaleDTO setDtoPlanCatalog(CaplUpsellSaleDTO dto) {
        entities.findInPlanCatalog(dto.getSubscribers().getCommercialPlanCd())
                .ifPresent(plan -> {
                    dto.setPlanCatalog(plan);
                    dto.setOriginFixedCharge(toDouble(plan.getChargeCodeOfPlan()));
                    dto.setPlanType(plan.getPlanDescription().toUpperCase().contains(ILIMITADO_CONDITION) ? ILIMITADO : CONTROL);
                });
        return dto;
    }

    /**
     * Establece las ofertas de Capl en el objeto CaplUpsellSaleDTO mediante la búsqueda en RedisEntities.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con las ofertas de Capl establecida si se encuentra en RedisEntities
     */
    public CaplUpsellSaleDTO setDtoPlanChangeOfferByOriginFC(CaplUpsellSaleDTO dto) {
        dto.setOfferType(REGULAR_OFFER_TYPE);

        getOfferByOriginFixedCharge(dto).ifPresent(offer -> {
            dto.setCaplOffer(offer);
            dto.setOfferFixedCharge(offer.getOfferFixedCharge());
            dto.setFixedChargeDifference(offer.getFixedChargeDifference());
            setDtoOfferCode(dto);
        });

        return dto;
    }

    /**
     * Este método establece el código del bono para la oferta en el objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    public void setDtoOfferCode(CaplUpsellSaleDTO dto) {
        entities.findOffer(dto.getCaplOffer().getBonusId()).ifPresent(o -> dto.getCaplOffer().setBonusCode(o.getCode()));
    }

    /**
     * Este método busca una oferta por su cargo fijo de origen.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene el cargo fijo de origen.
     * @return Un Optional de CaplOffer que contiene la oferta si se encuentra.
     */
    public Optional<CaplOffer> getOfferByOriginFixedCharge(CaplUpsellSaleDTO dto) {
        return entities.findPlanChangeOffers()
                .stream()
                .filter(offer -> Objects.nonNull(offer.getOfferType()) && Objects.nonNull(dto.getOfferType()))
                .filter(offer -> offer.getOfferType().contains(dto.getOfferType()))
                .filter(offer -> toDouble(offer.getOriginFixedCharge()) == dto.getOriginFixedCharge())
                .findFirst();
    }

    /**
     * Establece la promoción en el objeto CaplUpsellSaleDTO mediante la búsqueda en RedisEntities.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la promoción establecida si se encuentra en RedisEntities
     */
    public CaplUpsellSaleDTO setDtoPromotion(CaplUpsellSaleDTO dto) {
        entities.findInPromotion(Constant.PROMOTION_ID).ifPresent(dto::setPromotion);
        return dto;
    }

    /**
     * Establece la promoción en el objeto CaplUpsellSaleDTO mediante la búsqueda en RedisEntities.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la promoción establecida si se encuentra en RedisEntities
     */
    public CaplUpsellSaleDTO setDtoPlanChangeSale(CaplUpsellSaleDTO dto) {
        entities.findInPlanChangeSale(dto.getSubscriberCd()).ifPresent(dto::setCaplSale);
        return dto;
    }

    /**
     * Establece la descripcion de los SatPush en el objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la descripción de los SatPush
     */
    public CaplUpsellSaleDTO setDtoSatPushDescriptions(CaplUpsellSaleDTO dto) {
        dto.setFirstSatPush(formatMessage(dto.getTenor().getFirstSatPush(), dto));
        dto.setSecondSatPush(formatMessage(dto.getTenor().getSecondSatPush(), dto));

        System.out.println("first satPush" + dto.getTenor().getFirstSatPush());
        System.out.println("second satPush" + dto.getTenor().getSecondSatPush());

        return dto;
    }

    private String formatMessage(String message, CaplUpsellSaleDTO dto) {
        return message.replace(PLAN_FIXED_CHARGE, validateFields(dto.getOfferFixedCharge()))
                .replace(PLAN_GIGABYTES, validateFields(dto.getOfferGigabytesAmount()))
                .replace(PLAN_FC_DIFF, validateFields(dto.getFixedChargeDifference()))
                .replace(BONUS_GIGABYTES, validateFields(dto.getBonusGigabytesAmount()))
                .replace(BONUS_DURATION, validateFields(String.valueOf(dto.getBonusDuration())))
                .replace(BONUS_MONTH, validateFields(dto.getBonusMonth()))
                .replace(PLAN_NAME, validateFields(dto.getPlanNameAfterAcceptance()))
                .replace(DAY_ACT_NEW_PLAN, validateFields(dto.getActivationDayAfterAcceptance()))
                .replace(MONTH_ACT_NEW_PLAN, validateFields(dto.getActivationMonthAfterAcceptance()));
    }

    private String validateFields(String value) {
        return StringUtils.defaultIfEmpty(value, EMPTY);
    }

    /**
     * Guarda la venta de Capl en la entidad Redis.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    public void insertCAPLPreSalesLeadInCache(CaplUpsellSaleDTO dto) {
        CaplSale sale = setSatPushInfoByOperation(dto);
        sale.setTrackingId(dto.getStreamTrackingId());
        sale.setPhoneNumber(dto.getPhoneNumber());
        sale.setSubscriberCd(dto.getSubscribers().getSubscriberCd());
        sale.setDocumentNumber(dto.getSubscribers().getDocumentNumber());
        sale.setDocumentType(dto.getSubscribers().getDocumentType());
        sale.setPlanCd(dto.getSubscribers().getCommercialPlanCd());
        sale.setBillingCycle(getBillingCycle(dto.getSubscribers().getBillingCycle()));
        sale.setSatPushAccepted(Boolean.FALSE);
        sale.setExpiration(Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(Constant.CONTACT_POLICY));
        sale.setPlanType(dto.getPlanType());
        setCaplOffer(dto, sale);

        entities.saveInPlanChangeSale(dto.getSubscribers().getSubscriberCd(), sale);
    }

    /**
     * Obtiene el ciclo de facturación.
     *
     * @param billingCycle el ciclo de facturación
     * @return la cadena correspondiente al ciclo de facturación basada en el código de entrada
     */
    private String getBillingCycle(String billingCycle) {
        switch (billingCycle) {
            case TWENTY_FIVE:
                return BC_TWENTY_THREE;
            case EIGHTY_NINE:
                return String.valueOf(BILLING_CYCLE_FIVE);
            case NINETY_NINE:
                return BC_THIRTY_ONE;
            default:
                return billingCycle;
        }
    }

    /**
     * Este método establece los detalles del umbral SatPush en el objeto CaplUpsellSaleDTO. Establece las primeras y
     * segundas listas de SatPush, ID del bono, código del bono, duración del bono, diferencia de cargo fijo, cantidad
     * de gigabytes de la oferta, cantidad de gigabytes del bono y cargo fijo de la oferta desde el objeto CaplOffer en
     * el CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles de CaplOffer
     * @return el objeto CaplUpsellSaleDTO con los detalles del umbral SatPush establecidos
     */
    public CaplUpsellSaleDTO setDtoSatPushThresholdDetail(CaplUpsellSaleDTO dto) {
        setSatPushCommonDetail(dto);
        dto.setFixedChargeDifference(dto.getCaplOffer().getFixedChargeDifference());
        dto.setOfferGigabytesAmount(dto.getCaplOffer().getOfferGigabytesAmount());
        dto.setBonusGigabytesAmount(dto.getCaplOffer().getBonusGigabytesAmount());
        dto.setOfferFixedCharge(dto.getCaplOffer().getOfferFixedCharge());
        return dto;
    }

    /**
     * Establece la información comun para la formación de mensaje.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    private void setSatPushCommonDetail(CaplUpsellSaleDTO dto) {
        dto.setBonusId(StringUtils.defaultString(dto.getCaplOffer().getBonusId(), EMPTY));
        dto.setBonusCode(StringUtils.defaultString(dto.getCaplOffer().getBonusCode(), EMPTY));
        dto.setBonusDuration(NumberUtils.toInt(dto.getCaplOffer().getBonusDuration(), ZERO_INT));
        dto.setBonusMonth(StringUtils.defaultString(setBonusDuration(dto), EMPTY));
    }

    /**
     * Establece la duración del bono.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return la duración del bono
     */
    private String setBonusDuration(CaplUpsellSaleDTO dto) {
        return dto.getBonusDuration() > 1 ? dto.getTenor().getMonthTenor().get(1): dto.getTenor().getMonthTenor().get(0);
    }

    /**
     * Este método establece los detalles del flujo manual (SatPush) en el objeto CaplUpsellSaleDTO. Establece las
     * primeras y segundas listas de SatPush, ID del bono, código del bono, duración del bono, diferencia de cargo fijo,
     * cantidad de gigabytes de la oferta, cantidad de gigabytes del bono y cargo fijo de la oferta desde el objeto
     * CaplOffer en el CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles de CaplOffer
     * @return el objeto CaplUpsellSaleDTO con los detalles del flujo manual (SatPush) establecidos
     */
    public CaplUpsellSaleDTO setDtoSatPushManualFlowDetail(CaplUpsellSaleDTO dto) {
        setSatPushCommonDetail(dto);
        dto.setFixedChargeDifference(dto.getFixedChargeDifference());
        dto.setOfferGigabytesAmount(dto.getCaplOffer().getOfferGigabytesAmount());
        dto.setBonusGigabytesAmount(dto.getCaplOffer().getBonusGigabytesAmount());
        return dto;
    }

    /**
     * Guarda información del evento en la tabla CUSTOMER_CONTACT.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    public void saveCustomerContact(CaplUpsellSaleDTO dto, String canal) {
        campsysBusiness.insertCustomerContact(dto, canal);
    }

    /**
     * Construye y devuelve un objeto SatPushRequest basado en la información del objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return Un objeto SatPushRequest construido con la información del objeto CaplUpsellSaleDTO.
     */
    public SatPushRequest buildSatPushRequest(CaplUpsellSaleDTO dto) {
        SatPushRequest message = new SatPushRequest();
        message.setTrackingId(dto.getStreamTrackingId());
        message.setCampaignId(Constant.CAMPAIGN_ID);
        message.setPhoneNumber(dto.getPhoneNumber());
        message.setSubscriberId(dto.getSubscribers().getSubscriberCd());
        message.setMessageSender(Constant.MESSAGESENDER);
        message.setMessageType(Constant.MESSAGETYPE);
        message.setMessage1(dto.getFirstSatPush());
        message.setMessage2(dto.getSecondSatPush());
        message.setOfferId(getOfferId(dto.getBonusId()));
        message.setOfferCode(getOfferCode(dto.getBonusCode()));

        //System.out.println("DEBUG - SatPushRequest Construido: " + Utils.toJson(message));

        return message;
    }

    /**
     * Retorna el ID de la oferta o un valor por defecto si está vacío.
     *
     * @param offerId el ID de la oferta
     * @return el ID de la oferta o un valor por defecto si está vacío
     */
    private String getOfferId(String offerId) {
        return Strings.isBlank(offerId) ? String.valueOf(OFFERID) : offerId;
    }

    /**
     * Retorna el código de la oferta o un valor por defecto si está vacío.
     *
     * @param offerCode el código de la oferta
     * @return el código de la oferta o un valor por defecto si está vacío
     */
    private String getOfferCode(String offerCode) {
        return Strings.isBlank(offerCode) ? OFFERCODE : offerCode;
    }

    /**
     * Crea un objeto CaplUpsellSaleDTO a partir de SendingFeedbackRequest.
     *
     * @param sendingFeedbackRequest el objeto NotifyExternalSupRequest
     * @return el objeto CaplUpsellSaleDTO creado
     */
    public CaplUpsellSaleDTO fromSendingFeedbackToDTO(SendingFeedbackRequest sendingFeedbackRequest) {
        CaplUpsellSaleDTO dto = new CaplUpsellSaleDTO();
        dto.setStreamTrackingId(sendingFeedbackRequest.getResponseTrackingCd());
        dto.setEventId(sendingFeedbackRequest.getTrackingId());
        dto.setSendingFeedbackRequest(sendingFeedbackRequest);
        dto.setSubscriberCd(sendingFeedbackRequest.getSubscriberId());
        dto.setPhoneNumber(Constant.ZERO_STRING);
        return dto;
    }

    /**
     * Establece la venta de capl en el objeto CaplUpsellSaleDTO mediante la búsqueda en RedisEntities.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la venta de capl establecido si se encuentra en RedisEntities
     */
    public CaplUpsellSaleDTO setDtoPlanChangeSaleByFeedback(CaplUpsellSaleDTO dto) {
        entities.findInPlanChangeSale(dto.getSubscriberCd())
                .ifPresent(sale -> {
                            dto.setCaplSale(sale);
                            dto.setPhoneNumber(sale.getPhoneNumber());
                            dto.setPlanType(sale.getPlanType());
                        }
                );
        return dto;
    }

    public CaplUpsellSaleDTO setDtoAcceptanceSmsDetail(CaplUpsellSaleDTO dto) {
        CaplSale sale = dto.getCaplSale();
        dto.setBonusDuration(Utils.toInteger(sale.getBonusDuration()));
        dto.setBonusMonth(setBonusDuration(dto));
        dto.setOfferGigabytesAmount(sale.getOfferGigabytesAmount());
        dto.setBonusGigabytesAmount(sale.getBonusGigabytesAmount());
        return dto;
    }

    /**
     * Establece la descripcion del SMS en el objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la descripción del SMS
     */
    public CaplUpsellSaleDTO setDtoSmsAcceptanceDescriptions(CaplUpsellSaleDTO dto) {
        dto.setSms(formatMessage(dto.getTenor().getSms(), dto));

        System.out.println("DEBUG DTO Mapped: " + Utils.toJson(dto));

        return dto;
    }

    /**
     * Establece la logica de ciclo de facturacion para el tenor de la activación del nuevo cambio de plan.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la logica de ciclo de facturacion
     */
    public CaplUpsellSaleDTO setDtoBillingCycleLogic(CaplUpsellSaleDTO dto) {
        List<BillingCycleTenor> cycleLogics = entities.getBillingCycleLogic();
        String lengthOfMonth = String.valueOf(LocalDate.now().lengthOfMonth());

        List<BillingCycleTenor> cycleLogicsFinal = cycleLogics.stream()
                .filter(bc -> Objects.nonNull(bc.getBillingCycle()))
                .filter(bc -> bc.getBillingCycle().equals(dto.getCaplSale().getBillingCycle()))
                .filter(bc -> Objects.nonNull(bc.getNumberOfDaysInMonth()))
                .filter(bc -> bc.getNumberOfDaysInMonth().equalsIgnoreCase(lengthOfMonth) ||
                        bc.getNumberOfDaysInMonth().equalsIgnoreCase(ANY_MONTH))
                .sorted(Comparator.comparing(bc -> AUTOMATIC_SOURCE.equalsIgnoreCase(bc.getSource()) ? ZERO_INT : ONE_INT))
                .collect(Collectors.toList());
        dto.setBillingCycleTenors(cycleLogicsFinal);

        return dto;
    }

    /**
     * Establece la fecha de activación del nuevo plan.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con la fecha de activación del nuevo cambio de plan
     */
    public CaplUpsellSaleDTO setDtoActivationDateTenor(CaplUpsellSaleDTO dto) {
        try {
            dto.getBillingCycleTenors()
                    .stream()
                    .filter(bc -> evaluateAcceptanceLogic(bc.getSignAcceptanceLogic(), NumberUtils.toInt(bc.getDayAcceptanceLogic())))
                    .findFirst()
                    .ifPresent(bc -> {
                        String activationMonth = getActivationMonth(bc.getActivationMonth());
                        dto.setActivationMonthAfterAcceptance(activationMonth);
                        dto.setActivationDayAfterAcceptance(this.formDate(bc.getActivationDay()));
                    });
            return dto;
        } catch (Exception ex) {
            return dto;
        }
    }

    /**
     * Obtiene el mes de activación de nuevo plan.
     *
     * @param activationMonth el mes de activación en formato M, M+1, M+2, M+3, M+4
     * @return el mes de activación
     */
    private String getActivationMonth(String activationMonth) {
        LocalDate currentDate = LocalDate.now();

        if (activationMonth.equalsIgnoreCase(MONTH_SYMBOL)) {
            return this.formDate(String.valueOf(currentDate.getMonth().getValue()));
        }

        int monthValue = NumberUtils.toInt(activationMonth.replaceAll(REGEX_NO_DIGIT, EMPTY));
        return activationMonth.contains(PLUS)
                ? this.formDate(String.valueOf(currentDate.plusMonths(monthValue).getMonth().getValue()))
                : this.formDate(String.valueOf(currentDate.minusMonths(monthValue).getMonth().getValue()));
    }

    /**
     * Formatea la fecha.
     *
     * @param value el valor a formatear
     * @return el valor formateado
     */
    private String formDate(String value) {
        return value.length() == 1 ? ZERO_STRING.concat(value) : value;
    }

    /**
     * Evalua la logica de aceptacion.
     *
     * @param acceptanceLogic la logica de aceptacion
     * @param acceptanceDayLogic el dia de aceptacion
     * @return {@code true} si la logica de aceptacion es verdadera, {@code false} de lo contrario
     */
    private boolean evaluateAcceptanceLogic(String acceptanceLogic, Integer acceptanceDayLogic) {
        int currentDay = LocalDate.now().getDayOfMonth();
        String trimmedLogic = acceptanceLogic.trim().replace(SPACE, EMPTY);

        switch (trimmedLogic) {
            case LESS_OR_EQUAL_TO:
                return currentDay <= acceptanceDayLogic;
            case GREATER_OR_EQUAL_TO:
                return currentDay >= acceptanceDayLogic;
            case GREATER_THAN:
                return currentDay > acceptanceDayLogic;
            case LESS_THAN:
                return currentDay < acceptanceDayLogic;
            default:
                return currentDay == acceptanceDayLogic;
        }
    }

    /**
     * Establece el nombre del nuevo plan.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO con el nombre del nuevo plan
     */
    public CaplUpsellSaleDTO setDtoPlanNameTenor(CaplUpsellSaleDTO dto) {
        dto.setOfferType(getOfferType(dto.getCaplSale().getOperationCode()));
        entities.getPlanTenor()
                .stream()
                .filter(p -> p.getFixedCharge().equalsIgnoreCase(dto.getCaplSale().getOfferFixedCharge()))
                .filter(p -> p.getOfferType().equalsIgnoreCase(dto.getOfferType()))
                .filter(p -> validateRentType(dto.getCaplSale(), p, dto.getOfferType()))
                .min(Comparator.comparing(p -> AUTOMATIC_SOURCE.equalsIgnoreCase(p.getSource()) ? ZERO_INT : ONE_INT))
                .ifPresent(p -> dto.setPlanNameAfterAcceptance(p.getPlanComercialDesc()));
        return dto;
    }

    /**
     * Obtiene el tipo de oferta basado en el código de operación.
     *
     * @param operationCode el código de operación
     * @return el tipo de oferta, ya sea REGULAR_OFFER_TYPE o FAMILY_OFFER_TYPE o PORTA_OFFER_TYPE
     */
    private String getOfferType(String operationCode) {
        if (PORTA_MANUAL_FLOW_CODE.equalsIgnoreCase(operationCode)) {
            return PORTA_OFFER_TYPE;
        } else if (FAMILY_MANUAL_FLOW_CODE.equalsIgnoreCase(operationCode)) {
            return FAMILY_OFFER_TYPE;
        } else {
            return REGULAR_OFFER_TYPE;
        }
    }

    /**
     * Valida el tipo de renta basado en el tipo de oferta.
     *
     * @param sale el objeto CaplSale que contiene la información de la venta
     * @param planTenor el objeto PlanTenor que contiene la información del plan
     * @param offerType el tipo de oferta
     * @return {@code true} si el tipo de renta es válido, {@code false} de lo contrario
     */
    private boolean validateRentType(CaplSale sale, PlanTenor planTenor, String offerType) {
        return REGULAR_OFFER_TYPE.equalsIgnoreCase(offerType) ? Boolean.TRUE : planTenor.getRentType().equalsIgnoreCase(sale.getRentType());
    }

    /**
     * Establece el tenor de aceptación de la oferta de cambio de plan.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return el objeto CaplUpsellSaleDTO
     */
    public CaplUpsellSaleDTO setDtoSmsAcceptanceTenor(CaplUpsellSaleDTO dto) {
        String code = StringUtils.defaultString(dto.getCaplSale().getOperationCode(), REGULAR_MANUAL_FLOW_CODE);
        return getCaplUpsellSaleDTO(dto, code);
    }

    private CaplUpsellSaleDTO getCaplUpsellSaleDTO(CaplUpsellSaleDTO dto, String code) {
        String tenorKey = code;
        List<String> codesToDifferentiate = Arrays.asList(THRESHOLD_OP_CODE, EXHAUSTION_OP_CODE,REGULAR_MANUAL_FLOW_CODE);
        if (codesToDifferentiate.contains(code)) tenorKey = code + "_" + dto.getPlanType();
        entities.findInTenor(tenorKey).ifPresent(dto::setTenor);
        return dto;
    }

    /**
     * Actualiza un elemento de venta de capl en la entidad redis.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    public void updateCAPLPreSalesLeadInCache(CaplUpsellSaleDTO dto) {
        CaplSale sale = setSatPushInfoByOperation(dto);
        entities.saveInPlanChangeSale(dto.getSubscribers().getSubscriberCd(), sale);
    }

    /**
     * Establece los detalles de agotamiento para el SatPush en el objeto CaplUpsellSaleDTO. Establece las
     * primeras y segundas listas de SatPush, ID del bono, código del bono, duración del bono, diferencia de cargo fijo,
     * cantidad de gigabytes de la oferta, cantidad de gigabytes del bono y cargo fijo de la oferta desde el objeto
     * CaplSale en el CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles de CaplSale
     * @return el objeto CaplUpsellSaleDTO con los detalles de agotamiento establecidos para el SatPush
     */
    public CaplUpsellSaleDTO setDtoSatPushExhaustionDetail(CaplUpsellSaleDTO dto) {
        dto.setBonusId(dto.getCaplSale().getBonusId());
        dto.setBonusCode(dto.getCaplSale().getBonusCode());
        dto.setBonusDuration(NumberUtils.toInt(dto.getCaplSale().getBonusDuration(), ZERO_INT));
        dto.setBonusMonth(setBonusDuration(dto));
        dto.setFixedChargeDifference(dto.getCaplSale().getFixedChargeDifference());
        dto.setOfferGigabytesAmount(dto.getCaplSale().getOfferGigabytesAmount());
        dto.setBonusGigabytesAmount(dto.getCaplSale().getBonusGigabytesAmount());
        dto.setOfferFixedCharge(dto.getCaplSale().getOfferFixedCharge());

        System.out.println("DEBUG DTO Mapped: " + Utils.toJson(dto));

        return dto;
    }

    /**
     * Actualiza un elemento de venta de capl en la entidad redis.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    public void updateCAPLSaleInCache(CaplUpsellSaleDTO dto) {
        CaplSale sale = dto.getCaplSale();
        sale.setSatPushAccepted(Boolean.TRUE);
        sale.setAcceptanceDate(LocalDateTime.now().toString());
        sale.setTrackingIdSmsAcceptance(dto.getStreamTrackingId());
        sale.setAcceptanceEventId(dto.getEventId());
        entities.saveInPlanChangeSale(dto.getSubscriberCd(), sale);
    }

    /**
     * Actualiza un registro de venta de capl en la Oracle
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */
    public void updateCAPLSale(CaplUpsellSaleDTO dto) {
        campsysBusiness.updateCaplUpsellAcceptanceDetails(dto);
    }

    /**
     * Construye y devuelve un objeto SmsRequest basado en la información del objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @return Un objeto SmsRequest construido con la información del objeto CaplUpsellSaleDTO.
     */
    public SmsRequest buildSmsRequest(CaplUpsellSaleDTO dto, String valueSMS) {
        SmsRequest message = new SmsRequest();
        message.setTrackingId(dto.getStreamTrackingId());
        message.setCampaignId(Objects.nonNull(dto.getCaplTransaction()) ? CAMPAIGN_ID_2 : CAMPAIGN_ID);
        message.setPhoneNumber(dto.getPhoneNumber());
        message.setSubscriberId(dto.getSubscriberCd());
        message.setTemplateId(TEMPLATE_ID);
        //message.setMessageOffer(dto.getSms());
        message.setMessageOffer(Constant.SMS2.equals(valueSMS) ? dto.getTenor().getSecondSms() : dto.getSms());
        return message;
    }

    /**
     * Actualiza la venta de PlanChange en las entidades de Redis basado en el evento PostCapl. Establece el
     * ID de seguimiento para el SMS post-Capl y la fecha de contacto, luego guarda la venta actualizada en las
     * entidades de Redis.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles del evento PostCapl y el ID de seguimiento
     */
    public void updateCAPLPostSaleInCache(CaplUpsellSaleDTO dto) {
        entities.findInPlanChangeSale(dto.getSubscriberCd()).ifPresent(sale -> {
            sale.setTrackingIdSmsPostCapl(dto.getStreamTrackingId());
            sale.setSmsPostCaplContactDate(LocalDateTime.now().toString());
            sale.setPostCaplEventId(dto.getEventId());
            entities.saveInPlanChangeSale(dto.getSubscriberCd(), sale);
        });
    }

    /**
     * Actualiza la venta de CaplUpsell en la base de datos de Oracle basado en el evento PostCapl. Llama al
     * método updateCaplUpsellSMSConfirmation del servicio CampsysBusiness con el objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles del evento PostCapl
     */
    public void updateCAPLPostSale(CaplUpsellSaleDTO dto) {
        campsysBusiness.updateCaplUpsellSMSConfirmation(dto);
    }

    /**
     * Crea un objeto CaplUpsellSaleDTO a partir de CaplMainCustomer.
     *
     * @param caplMainCustomer el objeto CaplMainCustomer
     * @return el objeto CaplUpsellSaleDTO creado
     */
    public CaplUpsellSaleDTO fromCaplMainCustomerDTO(CaplMainCustomer caplMainCustomer) {
        CaplUpsellSaleDTO dto = new CaplUpsellSaleDTO();
        dto.setEventId(caplMainCustomer.getTrackingId());
        dto.setStreamTrackingId(ThreadLog.getTrackingId());
        dto.setPhoneNumber(StringUtils.defaultString(caplMainCustomer.getPhoneNumber(), EMPTY));
        dto.setOperationCode(StringUtils.defaultString(caplMainCustomer.getOperationCode(), EMPTY));
        dto.setCaplMainCustomer(caplMainCustomer);
        dto.setOfferType(getOfferType(caplMainCustomer.getOperationCode()));
        return dto;
    }

    /**
     * Este método guarda o actualiza la info del cliente en cache.
     *
     * @param dto el objeto CaplUpsellSaleDTO contiene detalles de la oferta
     */
    public void saveCAPLPreSaleInCache(CaplUpsellSaleDTO dto) {
        if (ObjectUtils.isEmpty(dto.getCaplSale())) {
            insertCAPLPreSalesLeadInCache(dto);
            return;
        }

        setCaplOffer(dto, dto.getCaplSale());
        updateCAPLPreSalesLeadInCache(dto);
    }

    /**
     * Este método establece los detalles de la oferta en el objeto CaplSale.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles de la oferta
     * @param sale el objeto CaplSale en el que se establecerán los detalles de la oferta
     */
    private void setCaplOffer(CaplUpsellSaleDTO dto, CaplSale sale) {
        sale.setOriginFixedCharge(String.valueOf(dto.getOriginFixedCharge()));
        sale.setOriginGigabytesAmount(dto.getCaplOffer().getOriginGigabytesAmount());
        sale.setOfferFixedCharge(dto.getOfferFixedCharge());
        sale.setOfferGigabytesAmount(dto.getCaplOffer().getOfferGigabytesAmount());
        sale.setFixedChargeDifference(dto.getFixedChargeDifference());
        sale.setRentType(setRentType(dto.getSubscribers()));
        sale.setOfferType(dto.getOfferType());
        sale.setGigabytesAmountDifference(dto.getCaplOffer().getGigabytesAmountDifference());
        sale.setBonusId(dto.getCaplOffer().getBonusId());
        sale.setBonusCode(dto.getCaplOffer().getBonusCode());
        sale.setBonusGigabytesAmount(dto.getCaplOffer().getBonusGigabytesAmount());
        sale.setBonusDuration(dto.getCaplOffer().getBonusDuration());
        sale.setTotalGigabytes(dto.getCaplOffer().getTotalGigabytes());
    }

    /**
     * Establece el tipo de renta del suscriptor.
     *
     * @param subscribers el objeto Subscribers
     * @return el tipo de renta del suscriptor
     */
    private String setRentType(Subscribers subscribers) {
        if (StringUtils.isBlank(subscribers.getRentType())) {
            return subscribers.getCommercialPlanDesc().contains(OVERDUE_RENT) ? OVERDUE_RENT : ADVANCE_RENT;
        }
        return subscribers.getRentType();
    }

    /**
     * Este método guarda o actualiza la info del cliente en BD.
     *
     * @param dto el objeto CaplUpsellSaleDTO contiene detalles de la oferta
     */
    public void saveCAPLPreSale(CaplUpsellSaleDTO dto) {
        Optional<CaplUpsellSale> transaction = campsysBusiness.findCAPLTransactionByPhoneNumber(dto);

        System.out.println("saveCAPLPreSale -> "+ transaction);

        if (transaction.isPresent()) {
            campsysBusiness.setCaplOffers(dto, transaction.get());
            updateTransaction(dto, transaction.get());
        } else {
            saveTransaction(dto);
        }
    }

    /**
     * Establece el Tenor.
     * Construye una llave dinámica para los flujos que lo requieren (UB, XB)
     * y utiliza la llave directa para los demás flujos (MF_R, MF_F, MF_P, etc.).
     *
     * @param dto el objeto CaplUpsellSaleDTO que ya contiene el planType
     * @return el objeto CaplUpsellSaleDTO con el Tenor correcto establecido
     */
    public CaplUpsellSaleDTO setDtoSatPushTenor(CaplUpsellSaleDTO dto) {
        String operationCode = dto.getOperationCode();
        return getCaplUpsellSaleDTO(dto, operationCode);
    }

    /**
     * Este método guarda la transacción en BD.
     *
     * @param dto el objeto CaplUpsellSaleDTO contiene detalles de la oferta
     */
    private void saveTransaction(CaplUpsellSaleDTO dto) {
        CaplUpsellSale caplUpsellSale = setSatPushInfoByOperation(dto, null);
        dto.setCaplUpsellSale(caplUpsellSale);

        campsysBusiness.insertCAPLPreSalesLead(dto);
    }

    /**
     * Este método actualiza el origen del evento en BD.
     *
     * @param dto el objeto CaplUpsellSaleDTO contiene detalles de la oferta
     */
    private void updateTransaction(CaplUpsellSaleDTO dto, CaplUpsellSale o) {
        CaplUpsellSale caplUpsellSale = setSatPushInfoByOperation(dto, o);
        dto.setCaplUpsellSale(caplUpsellSale);

        campsysBusiness.updateCAPLPreSalesLead(dto);
    }

    /**
     * Este método crea o setea información del origen del evento en CaplSale que se guarda en cache.
     *
     * @param dto el objeto CaplUpsellSaleDTO contiene detalles de la oferta
     * @return el objeto CaplSale creado
     */
    private CaplSale setSatPushInfoByOperation(CaplUpsellSaleDTO dto) {
        CaplSale sale = ObjectUtils.isNotEmpty(dto.getCaplSale()) ? dto.getCaplSale() : new CaplSale();

        switch (dto.getOperationCode()) {
            case THRESHOLD_OP_CODE: {
                sale.setTrackingIdSatPushOffer1(dto.getStreamTrackingId());
                sale.setThresholdEventId(dto.getEventId());
                sale.setThresholdContactDate(LocalDateTime.now().toString());
                break;
            }
            case EXHAUSTION_OP_CODE: {
                sale.setTrackingIdSatPushOffer2(dto.getStreamTrackingId());
                sale.setExhaustionEventId(dto.getEventId());
                sale.setExhaustionContactDate(LocalDateTime.now().toString());
                break;
            }
            default: {
                sale.setTrackingIdSatPushOffer3(dto.getStreamTrackingId());
                sale.setManualFlowEventId(dto.getEventId());
                sale.setManualFlowContactDate(LocalDateTime.now().toString());
            }

        }

        sale.setExpiration(Instant.now().getEpochSecond() + TimeUnit.DAYS.toSeconds(Constant.CONTACT_POLICY));
        sale.setOperationCode(dto.getOperationCode());
        sale.setPlanType(dto.getPlanType());

        return sale;
    }

    /**
     * Este método crea o setea información del origen del evento en CaplSale que se guarda en BD.
     *
     * @param dto el objeto CaplUpsellSaleDTO contiene detalles de la oferta
     * @param caplUpsellSale crea o actualiza el objeto con info del origen del evento
     * @return el objeto CaplUpsellSale creado
     */
    private CaplUpsellSale setSatPushInfoByOperation(CaplUpsellSaleDTO dto, CaplUpsellSale caplUpsellSale) {
        CaplUpsellSale sale = ObjectUtils.isNotEmpty(caplUpsellSale) ? caplUpsellSale : new CaplUpsellSale();

        switch (dto.getOperationCode()) {
            case THRESHOLD_OP_CODE: {
                sale.setTrackingIdSatPushOffer1(dto.getStreamTrackingId());
                sale.setThresholdEventId(dto.getEventId());
                sale.setThresholdContactDate(LocalDateTime.now());
                break;
            }
            case EXHAUSTION_OP_CODE: {
                sale.setTrackingIdSatPushOffer2(dto.getStreamTrackingId());
                sale.setExhaustionEventId(dto.getEventId());
                sale.setExhaustionContactDate(LocalDateTime.now());
                break;
            }
            default: {
                sale.setTrackingIdSatPushOffer3(dto.getStreamTrackingId());
                sale.setManualFlowEventId(dto.getEventId());
                sale.setManualFlowContactDate(LocalDateTime.now());
            }

        }

        sale.setOperationCode(dto.getOperationCode());

        return sale;
    }


    /**
     * Establece los cargos fijos de tipo origen(con descuento o sin descuento),
     * oferta y la diferencia entre oferta y origen.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene el cargo fijo de la oferta
     * @return el objeto CaplUpsellSaleDTO con la oferta de cambio de plan establecida
     */
    public CaplUpsellSaleDTO setDtoFixedCharges(CaplUpsellSaleDTO dto) {
        double offerFixedCharge = toDouble(dto.getCaplMainCustomer().getOfferFixedCharge());
        double originFixedCharge = dto.getOriginFixedCharge();
        double netOriginFixedCharge = Arrays.stream(GROUP_OFFER_TYPE)
                .anyMatch(c -> dto.getOfferType().contains(c))
                ? originFixedCharge
                : getNetFixedCharge(dto);
        double fixedChargeDifference = offerFixedCharge - netOriginFixedCharge;
        int precision = Arrays.stream(GROUP_OFFER_TYPE)
                .anyMatch(c -> dto.getOfferType().contains(c))
                ? NumberUtils.INTEGER_ZERO
                : NumberUtils.INTEGER_TWO;

        dto.setOfferFixedCharge(String.valueOf(offerFixedCharge));
        dto.setFixedChargeDifference(shortenDouble(fixedChargeDifference, precision));
        return dto;
    }

    /**
     * Obtiene el cargo fijo neto.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return el cargo fijo neto
     */
    private Double getNetFixedCharge(CaplUpsellSaleDTO dto) {
        if (dto.getSubscribers().getFlagDiscount().equalsIgnoreCase(ONE_STRING)) {
            return toDouble(dto.getSubscribers().getNetFixedCharge());
        } else {
            return dto.getOriginFixedCharge();
        }
    }

    /**
     * Establece la oferta de cambio de plan en el objeto CaplUpsellSaleDTO basado en el cargo fijo de la oferta.
     * Primero, busca la oferta de cambio de plan que tiene el mismo cargo fijo de la oferta que el dto.
     * Si la oferta está presente, establece la oferta y el código de bono en el dto.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene el cargo fijo de la oferta
     * @return el objeto CaplUpsellSaleDTO con la oferta de cambio de plan establecida
     */
    public CaplUpsellSaleDTO setDtoPlanChangeOfferByFixedCharge(CaplUpsellSaleDTO dto) {
        if (REGULAR_MANUAL_FLOW_CODE.equalsIgnoreCase(dto.getOperationCode())) {
            entities.findPlanChangeOffers()
                    .stream()
                    .filter(offer -> toDouble(offer.getOfferFixedCharge()) ==  toDouble(dto.getOfferFixedCharge()))
                    .filter(offer -> Objects.nonNull(offer.getOfferType()) && Objects.nonNull(dto.getOfferType()))
                    .filter(offer -> dto.getOfferType().equalsIgnoreCase(offer.getOfferType()))
                    .findFirst()
                    .ifPresent(offer -> setRegularCaplOffer(dto, offer));
        } else {
            getOfferByOriginFixedCharge(dto).ifPresent(offer -> setFamilyCaplOffer(dto, offer));
        }
        setDtoOfferCode(dto);
        return dto;
    }

    /**
     * Establece oferta de tipo plan familia.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     */
    private void setRegularCaplOffer(CaplUpsellSaleDTO dto, CaplOffer offer) {
        dto.setCaplOffer(offer);
        if (toDouble(offer.getOriginFixedCharge()) != dto.getOriginFixedCharge()) {
            setGigabytesAmountOriginAndDifference(dto, offer);
        }
    }

    /**
     * Establece oferta de tipo regular.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     */
    private void setFamilyCaplOffer(CaplUpsellSaleDTO dto, CaplOffer offer) {
        dto.setCaplOffer(offer);
        if (Strings.isBlank(offer.getOriginGigabytesAmount()) || Strings.isBlank(offer.getGigabytesAmountDifference())) {
            setGigabytesAmountOriginAndDifference(dto, offer);
        }
    }

    /**
     * Establece la cantidad de gigabytes de origen y la diferencia de gigabytes.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @param offer el objeto CaplOffer
     */
    private void setGigabytesAmountOriginAndDifference(CaplUpsellSaleDTO dto, CaplOffer offer) {
        if (Objects.nonNull(dto.getPlanCatalog())) {
            double originGigabytesAmount = toInteger(extractNumbersFromString(dto.getPlanCatalog().getAmountMbPlan()));
            double offerGigabytesAmount = toInteger(offer.getOfferGigabytesAmount());
            double gigabytesDifference = offerGigabytesAmount - originGigabytesAmount;
            dto.getCaplOffer().setOriginGigabytesAmount(String.valueOf(originGigabytesAmount));
            dto.getCaplOffer().setGigabytesAmountDifference(String.valueOf(gigabytesDifference));
        } else {
            getOfferByOriginFixedCharge(dto).ifPresent(o -> {
                dto.getCaplOffer().setOriginGigabytesAmount(o.getOriginGigabytesAmount());
                dto.getCaplOffer().setGigabytesAmountDifference(o.getGigabytesAmountDifference());
            });
        }
    }

    /**
     * Este método actualiza el estado del cliente en la bd.
     * Llama al método updateCustomerStatus del servicio CampsysBusiness con el objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     */
    public void updateCustomerStatus(CaplUpsellSaleDTO dto) {
        campsysBusiness.updateCustomerStatus(dto);
    }

    /**
     * Guarda la información de contactabilidad en Redis.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     */
    public void saveContactability(CaplUpsellSaleDTO dto) {
        ZonedDateTime midNight = ZonedDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT, ZoneId.of(TIME_ZONE));

        long expiration = midNight.toEpochSecond();

        Contactability contactability = new Contactability();
        contactability.setCampaignId(CAMPAIGN_ID);
        contactability.setSubscriberCd(dto.getSubscribers().getSubscriberCd());
        contactability.setPhoneNumber(dto.getPhoneNumber());
        contactability.setNumContactos(String.valueOf(ONE_INT));
        contactability.setExpiration(expiration);

        entities.saveInContactability(dto.getPhoneNumber(), contactability);
    }

    /**
     * Crea un objeto CaplUpsellSaleDTO a partir de CaplTransaction.
     *
     * @param caplTransaction el objeto NotifyExternalSupRequest
     * @return el objeto CaplUpsellSaleDTO creado
     */
    public CaplUpsellSaleDTO fromCaplTransactionToDTO(CaplTransaction caplTransaction) {
        CaplUpsellSaleDTO dto = new CaplUpsellSaleDTO();
        dto.setStreamTrackingId(ThreadLog.getTrackingId());
        dto.setEventId(caplTransaction.getTrackingId());
        dto.setCaplTransaction(caplTransaction);
        dto.setSubscriberCd(caplTransaction.getSubscriberId());
        dto.setPhoneNumber(caplTransaction.getPhoneNumber());
        return dto;
    }

    /**
     * Establece el Tenor para el Sms Post Capl.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return el objeto CaplUpsellSaleDTO con el Tenor de umbral de SatPush establecido
     */
    public CaplUpsellSaleDTO setDtoSmsCaplTenor(CaplUpsellSaleDTO dto) {
        entities.findInTenor(POST_CAPL_CODE).ifPresent(dto::setTenor);
        return dto;
    }

    /**
     * Establece el mensaje SMS para el evento PostCapl en el objeto CaplUpsellSaleDTO. El mensaje se
     * formatea utilizando la plantilla de SMS post-Capl de la promoción y los detalles de la oferta del evento
     * PostCapl.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene los detalles del evento PostCapl y la promoción
     * @return el objeto CaplUpsellSaleDTO con el mensaje SMS establecido
     */
    public CaplUpsellSaleDTO setDtoSmsCaplDescriptions(CaplUpsellSaleDTO dto) {
        dto.setOfferGigabytesAmount(dto.getCaplSale().getOfferGigabytesAmount());
        dto.setSms(formatMessage(dto.getTenor().getSms(), dto));
        return dto;
    }

    /**
     * Guarda flag de envío del SMS de aceptación y SMS recordatorio en Redis
     *
     * @param dto el objeto CaplUpsellSaleDTO
     */

    public void updateSendSMSInCache(CaplUpsellSaleDTO dto, String valueSMS) {
        Optional.ofNullable(dto)
                .map(CaplUpsellSaleDTO::getCaplSale)
                .filter(sale -> updateSaleFlag(sale, valueSMS))
                .ifPresent(sale -> entities.saveInPlanChangeSale(dto.getSubscriberCd(), sale));
    }

    private boolean updateSaleFlag(CaplSale sale, String valueSMS) {
        if (Constant.SMS.equals(valueSMS)) {
            sale.setFlagFirstSms(Boolean.TRUE);
            return true;
        } else if (Constant.SMS2.equals(valueSMS)) {
            sale.setFlagSecondSms(Boolean.TRUE);
            return true;
        } else {
            log.warn("Tipo de SMS no válido: {}", valueSMS);
            return false;
        }
    }

    /**
     * Contruye el SMS Request y envia el SMS mediante el servicio Sending SMS.
     *
     * @param dto el objeto CaplUpsellSaleDTO
     * @param valueSMS el tipo de SMS a enviar
     */
    public void sendSmsNotification(CaplUpsellSaleDTO dto, String valueSMS) {
        if (dto == null || valueSMS == null) {
            log.warn("SMS notification not sent: dto or valueSMS is null");
            return;
        }

        Optional<SmsRequest> smsRequest = buildSmsRequestByType(valueSMS, dto);
        if (smsRequest.isPresent()) {
            String jsonRequest = Utils.toJson(smsRequest.get());
            sendingFeedbackService.sendSmsAcceptance(jsonRequest);
        } else {
            log.warn("No SMS request built for type: {}", valueSMS);
        }
    }

    /**
     * Construye un SmsRequest según el tipo de SMS especificado.
     *
     * @param smsType el tipo de SMS (SMS o SMS2)
     * @param dto el objeto CaplUpsellSaleDTO
     * @return un Optional con el SmsRequest construido
     */
    private Optional<SmsRequest> buildSmsRequestByType(String smsType, CaplUpsellSaleDTO dto) {
        // Si el tipo es SMS o SMS2, procedemos. Si hay más tipos, agrégalos al OR.
        if (Constant.SMS.equals(smsType) || Constant.SMS2.equals(smsType)) {
            return Optional.ofNullable(buildSmsRequest(dto, smsType));
        }

        log.warn("SMS type not recognized: {}", smsType);
        return Optional.empty();
    }
}
