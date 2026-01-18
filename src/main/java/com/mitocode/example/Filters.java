package com.tdp.ms.caplupsellsale.business;

import static com.tdp.ms.caplupsellsale.commons.Constant.*;
import static com.tdp.ms.caplupsellsale.commons.Utils.toDouble;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.tdp.ms.caplupsellsale.entity.redis.Contactability;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import com.tdp.ms.caplupsellsale.commons.Constant;
import com.tdp.ms.caplupsellsale.commons.StreamProperties;
import com.tdp.ms.caplupsellsale.commons.Utils;
import com.tdp.ms.caplupsellsale.dto.CaplUpsellSaleDTO;
import com.tdp.ms.caplupsellsale.redis.RedisEntities;

import lombok.RequiredArgsConstructor;

/**
 * Contiene informacion de los filtros realizados.
 *
 * @version 0.1 - Released on November 28, 2023
 * @author: Elizabeth Valdez, Indra Company
 */
@Service
@RequiredArgsConstructor
public class Filters {

    private final RedisEntities    entities;
    private final StreamProperties properties;


    /**
     * Verifica si el número de teléfono del evento se encuentra en la lista blanca.
     *
     * @param dto El objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el número de teléfono está en la lista blanca, {@code false} de lo contrario.
     */
    public boolean isInWhiteList(CaplUpsellSaleDTO dto) {
        if (properties.isWhiteListFlagOn())
            return entities.isInWhiteList(dto.getPhoneNumber());
        return true;
    }

    /**
     * Verifica si el codigo de operacion del evento es XB(Agotamiento).
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el codigo de operacion es XB, {@code false} de lo contrario.
     */
    public boolean isValidOperationCode(CaplUpsellSaleDTO dto) {
        return Arrays.asList(THRESHOLD_OP_CODE, EXHAUSTION_OP_CODE).contains(dto.getOperationCode());
    }

    /**
     * Verifica si el bolton del evento esta en la lista de boltons válidos.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el bolton del evento esta en la lista, {@code false} de lo contrario.
     */
    public boolean isInBoltonList(CaplUpsellSaleDTO dto) {
        return entities.isInBoltonList(dto.getBoltonCode());
    }

    /**
     * Verifica si el objeto de suscriptores es non-{@code null}.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el objeto de suscriptores está presente, {@code false} de lo contrario.
     */
    public boolean isSubscribersPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getSubscribers());
    }

    /**
     * Verifica si el plan no es LMA(ex-plan familia).
     *
     * @param dto CaplUpsellSaleDTO
     * @return {@code true} si el plan no es LMA, {@code false} de lo contrario.
     */
    public boolean isNotLMAPlan(CaplUpsellSaleDTO dto) {
        return dto.getOperationCode().contains(PORTA_MANUAL_FLOW_CODE) || entities.isNotInAdditionalPlans(dto.getSubscribers().getCommercialPlanCd());
    }

    /**
     * Verifica si la linea tiene una oferta de cambio de plan aceptada.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la linea tiene una oferta de cambio de plan aceptada, {@code false} de lo contrario.
     */
    public boolean isNotInPlanChangeSale(CaplUpsellSaleDTO dto) {
        boolean acceptedOffer = Boolean.FALSE;
        if (Objects.nonNull(dto.getCaplSale())) {
            acceptedOffer = dto.getCaplSale().getSatPushAccepted();
        }
        return !acceptedOffer;
    }

    /**
     * Verifica si el segmento de la linea es residencial.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el segmento del cliente es residencial, {@code false} de lo contrario.
     */
    public boolean isResidential(CaplUpsellSaleDTO dto) {
        return RESIDENTIAL.equalsIgnoreCase(dto.getSubscribers().getCustomerSegmentDesc())
                || Arrays.asList(C, DNI, P).contains(dto.getSubscribers().getDocumentType().toUpperCase());
    }

    /**
     * Verifica si la linea no es MT(Movistar Total).
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la linea no es MT(Movistar Total), {@code false} de lo contrario.
     */
    public boolean isNotMovistarTotal(CaplUpsellSaleDTO dto) {
        return Constant.ZERO_STRING.equals(dto.getSubscribers().getFlagmt());
    }

    /**
     * Verifica si la linea no esta en la lista negra.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la linea no esta en la lista negra, {@code false} de lo contrario.
     */
    public boolean isNotBlackList(CaplUpsellSaleDTO dto) {
        return Constant.ZERO_STRING.equals(dto.getSubscribers().getFlagIndBlackList());
    }

    /**
     * Verifica si el suscriptor ha tenido deudas en los últimos tres meses.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información.
     * @return {@code true} si el suscriptor no ha tenido deudas en los últimos tres meses, {@code false} de lo
     * contrario.
     */
    public boolean hasNoDebtAnyMonth(CaplUpsellSaleDTO dto) {
        return !dto.isHasDebtAllMonths();
    }

    /**
     * Verifica si el suscriptor no ha tenido deudas en el mes 3.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información.
     * @return {@code true} si el suscriptor no ha tenido deudas en el mes 3, {@code false} de lo contrario.
     */
    public boolean hasNoDebtM3(CaplUpsellSaleDTO dto) {
        return !dto.isHasDebtM3();
    }

    /**
     * Verifica si el suscriptor no ha tenido deudas en los meses 2 y 3.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información.
     * @return {@code true} si el suscriptor no ha tenido deudas en los meses 2 y 3, {@code false} de lo contrario.
     */
    public boolean hasNoDebtM2M3(CaplUpsellSaleDTO dto) {
        return !dto.isHasDebtM2M3();
    }


    /**
     * Verifica si el suscriptor no ha tenido deudas en el mes 2.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información.
     * @return {@code true} si el suscriptor no ha tenido deudas en el mes 2, {@code false} de lo contrario.
     */
    public boolean hasNoDebtM2(CaplUpsellSaleDTO dto) {
        return !dto.isHasDebtM2();
    }

    /**
     * Verifica si el suscriptor ha pagado algunos recibos.
     *
     * @param dto El objeto CaplUpsellSaleDTO que contiene la información.
     * @return {@code true} si el suscriptor ha pagado algunos recibos, {@code false} de lo contrario.
     */
    public boolean hasPaidSomeReceipts(CaplUpsellSaleDTO dto) {
        return dto.isHasPaidSomeReceipts();
    }

    /**
     * Verifica si el tipo de producto de la linea no es prepago, ni caribu.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el tipo de producto no es prepago, ni caribu, {@code false} de lo contrario.
     */
    public boolean isValidProductType(CaplUpsellSaleDTO dto) {
        List<String> validProductType = Utils.convertListToUpperCase(dto.getPromotion().getProductTypeLine());
        return validProductType.contains(dto.getSubscribers().getProductTypeDesc().toUpperCase());
    }

    /**
     * Valida si el plan esta presente.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el plan esta presente, {@code false} de lo contrario.
     */
    public boolean isPlanCatalogPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getPlanCatalog());
    }

    /**
     * Verifica si el cargo fijo del plan de la linea es mayor a cero.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el cargo fijo de la linea es mayor a cero, {@code false} de lo contrario.
     */
    public boolean isFixedChargeOriginGreaterThanZero(CaplUpsellSaleDTO dto) {
        return dto.getOriginFixedCharge() > ZERO_INT;
    }

    /**
     * Verifica si la linea no ha tenido un CAPL en los 2 ultimos meses.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la linea no ha tenido un CAPL en los 2 ultimos meses, {@code false} de lo contrario.
     */
    public boolean isNotInCaplRequest(CaplUpsellSaleDTO dto) {
        return !entities.findInCaplRequest(dto.getPhoneNumber()).isPresent();
    }

    /**
     * Verifica si el objeto de oferta de cambio plan es non-{@code null}.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el objeto de Oferta de Cambio Plan está presente, {@code false} de lo contrario.
     */
    public boolean isPlanChangeOfferPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getCaplOffer());
    }

    /**
     * Verifica si el objeto de la promocion es non-{@code null}.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el objeto de la promocion está presente, {@code false} de lo contrario.
     */
    public boolean isPromotionPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getPromotion());
    }

    /**
     * Verifica si la campaña se encuentra vigente.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la campaña se encuentra vigente, {@code false} de lo contrario.
     */
    public boolean isValidDatePromotion(CaplUpsellSaleDTO dto) {
        return Utils.validateDate(dto.getPromotion().getStartValidateDate(), dto.getPromotion().getEndValidateDate());
    }

    /**
     * Verifica si el id de la campaña es valido.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el id de la campaña es valido, {@code false} de lo contrario.
     */
    public boolean isValidCampaignId(CaplUpsellSaleDTO dto) {
        return Constant.CAMPAIGN_ID.equalsIgnoreCase(dto.getSendingFeedbackRequest().getCampaignId());
    }

    /**
     * Verifica si el objeto de venta de cambio plan es non-{@code null}.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el objeto de venta de cambio plan está presente, {@code false} de lo contrario.
     */
    public boolean isPlanChangeSalePresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getCaplSale());
    }

    /**
     * Verifica si la lógica del ciclo de facturación está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si la lógica del ciclo de facturación está presente, {@code false} en caso contrario.
     */
    public boolean isBillingCycleLogicPresent(CaplUpsellSaleDTO dto) {
        return !dto.getBillingCycleTenors().isEmpty();
    }

    /**
     * Verifica si la lógica del ciclo de facturación está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si la lógica del ciclo de facturación está presente, {@code false} en caso contrario.
     */
    public boolean isActivationDateTenorPresent(CaplUpsellSaleDTO dto) {
        return Strings.isNotBlank(dto.getActivationDayAfterAcceptance()) &&
                Strings.isNotBlank(dto.getActivationMonthAfterAcceptance());
    }

    /**
     * Verifica si el Tenor del Nombre del Plan está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si el Tenor del Nombre del Plan está presente, {@code false} en caso contrario.
     */
    public boolean isPlanNameTenorPresent(CaplUpsellSaleDTO dto) {
        return Strings.isNotBlank(dto.getPlanNameAfterAcceptance());
    }

    /**
     * Verifica si la linea tiene una oferta de cambio de plan aceptada.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la linea tiene una oferta de cambio de plan aceptada, {@code false} de lo contrario.
     */
    public boolean isCaplSalePresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getCaplSale());
    }

    /**
     * Verifica si la fecha de contacto del umbral está presente en el objeto CaplUpsellSaleDTO.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si la fecha de contacto del umbral no está vacía o nula, {@code false} en caso contrario.
     */
    public boolean isThresholdPresent(CaplUpsellSaleDTO dto) {
        return Strings.isNotBlank(dto.getCaplSale().getThresholdContactDate());
    }

    /**
     * Verifica si la oferta no ha sido aceptada.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si la oferta no ha sido aceptada, {@code false} en caso contrario.
     */
    public boolean isOfferNotAccepted(CaplUpsellSaleDTO dto) {
        if (ObjectUtils.isEmpty(dto.getCaplSale()))
            return Boolean.TRUE;

        return Boolean.FALSE.equals(dto.getCaplSale().getSatPushAccepted());
    }

    /**
     * Verifica si el Tenor de SatPush está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si el Tenor de SatPush está presente, {@code false} en caso contrario.
     */
    public boolean isSatPushTenorPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getTenor())
                && !dto.getTenor().getFirstSatPush().isEmpty()
                && !dto.getTenor().getSecondSatPush().isEmpty();
    }

    /**
     * Verifica si el Tenor de Aceptación de SMS está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si el Tenor de Aceptación de SMS está presente, {@code false} en caso contrario.
     */
    public boolean isSmsAcceptanceTenorPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getTenor()) && !dto.getTenor().getSms().isEmpty();
    }

    /**
     * Verifica si el Tenor de SMS Post-Capl está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} si el Tenor de SMS Post-Capl está presente, {@code false} en caso contrario.
     */
    public boolean isCaplSmsTenorPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getTenor()) && Objects.nonNull(dto.getTenor().getSms());
    }

    /**
     * Verifica si el tipo de oferta es plan familia, porta o regular.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información
     * @return {@code true} ssi el tipo de oferta es plan familia o regular, {@code false} en caso contrario.
     */
    public boolean isValidOfferType(CaplUpsellSaleDTO dto) {
        return Stream.of(FAMILY_OFFER_TYPE, REGULAR_OFFER_TYPE, PORTA_OFFER_TYPE)
                .anyMatch(c -> dto.getOfferType().contains(c));

    }

    /**
     * Verifica si el cargo fijo origen neto no es nulo cuando es oferta plan familia,
     * cuando es oferta regular no se valida.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el cargo fijo origen neto es valido, {@code false} de lo contrario.
     */
    public boolean isValidNetOriginFixedCharge(CaplUpsellSaleDTO dto) {
        if (REGULAR_OFFER_TYPE.equalsIgnoreCase(dto.getOfferType()) || dto.getOfferType().contains(PORTA_OFFER_TYPE)) return true;
        return Strings.isNotBlank(dto.getSubscribers().getNetFixedCharge());
    }

    /**
     * Verifica si el cargo fijo origen sea igual al cargo fijo origen de la base potencial.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si coinciden; {@code false} en caso contrario.
     */
    public boolean isValidOriginFixedCharge(CaplUpsellSaleDTO dto) {
        if (Objects.isNull(dto.getPlanCatalog()) && Objects.isNull(dto.getOriginFixedCharge())) {
            dto.setOriginFixedCharge(toDouble(dto.getCaplMainCustomer().getOriginFixedCharge()));
        }
        return dto.getOriginFixedCharge() == toDouble(dto.getCaplMainCustomer().getOriginFixedCharge());
    }


    /**
     * Verifica si el cargo fijo de la oferta es mayor a cero.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el cargo fijo de la oferta es mayor a cero, {@code false} de lo contrario.
     */
    public boolean isFixedChargeOfferGreaterThanZero(CaplUpsellSaleDTO dto) {
        return toDouble(dto.getOfferFixedCharge()) > NumberUtils.DOUBLE_ZERO;
    }

    /**
     * Verifica si el cargo fijo de la oferta es mayor a cero.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si el cargo fijo de la oferta es mayor a cero, {@code false} de lo contrario.
     */
    public boolean isFixedChargeDiffGreaterThanZero(CaplUpsellSaleDTO dto) {
        return toDouble(dto.getFixedChargeDifference()) > NumberUtils.DOUBLE_ZERO;
    }

    /**
     * Verifica si la oferta es upsell.
     *
     * @param dto el objeto CaplDiscountDTO que contiene la información
     * @return {@code true} si la oferta es upsell, {@code false} de lo contrario.
     */
    public boolean isGigabytesAmountDiffGreaterThanZero(CaplUpsellSaleDTO dto) {
        return PORTA_OFFER_TYPE.equalsIgnoreCase(dto.getOfferType()) || toDouble(dto.getCaplOffer().getGigabytesAmountDifference()) > NumberUtils.DOUBLE_ZERO;
    }

    /**
     * Verifica si el cliente no está en la lista de contactabilidad.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si el cliente no está en la lista de contactabilidad, {@code false} en caso contrario.
     */
    public boolean isNotInContactability(CaplUpsellSaleDTO dto) {
        Optional<Contactability> findContactability = entities.findInContactability(dto.getPhoneNumber());
        return !findContactability.isPresent();
    }

    /**
     * Verifica si es una transacción CAPL.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si es una transacción CAPL, {@code false} en caso contrario.
     */
    public boolean isCaplTransaction(CaplUpsellSaleDTO dto) {
        return TRANSACTION_TYPE_CAPL.equalsIgnoreCase(dto.getCaplTransaction().getTransactionType());
    }

    /**
     * Verifica si es una transacción con estado cerrado.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si es una transacción con estado cerrado, {@code false} en caso contrario.
     */
    public boolean isClosedTransaction(CaplUpsellSaleDTO dto) {
        return TRANSACTION_STATUS.equalsIgnoreCase(dto.getCaplTransaction().getStatusDesc());
    }

    /**
     * Verifica si el campo subscriberCd está presente.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si el campo subscriberCd está presente, {@code false} en caso contrario.
     */
    public boolean isSubscriberCdPresent(CaplUpsellSaleDTO dto) {
        return Objects.nonNull(dto.getSubscriberCd());
    }

    /**
     * Verifica si el cargo fijo de origen en el evento coincide con el cargo fijo de origen
     * registrado en el momento de aceptación de CAPL.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si coinciden; {@code false} en caso contrario.
     */
    public boolean isValidSourceFixedCharge(CaplUpsellSaleDTO dto) {
        return dto.getCaplSale().getOriginFixedCharge().equals(dto.getCaplTransaction().getSourceFixedCharge());
    }

    /**
     * Verifica si el plan de origen en el evento coincide con el plan de origen
     * registrado en el momento de aceptación de CAPL.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si coinciden; {@code false} en caso contrario.
     */
    public boolean isValidSourcePlanId(CaplUpsellSaleDTO dto) {
        return dto.getCaplSale().getPlanCd().equals(dto.getCaplTransaction().getSourcePlanCode());
    }

    /**
     * Verifica si el cargo fijo de destino en el evento coincide con el cargo fijo de destino
     * registrado en el momento de aceptación de CAPL.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si coinciden; {@code false} en caso contrario.
     */
    public boolean isValidDestFixedCharge(CaplUpsellSaleDTO dto) {
        return dto.getCaplSale().getOfferFixedCharge().equals(dto.getCaplTransaction().getDestFixedCharge());
    }

    /**
     * Verifica si el canal es Massive Channel.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si el canal es Massive Channel, {@code false} en caso contrario.
     */
    public boolean isMassiveChannel(CaplUpsellSaleDTO dto) {
        return CHANNEL_CAPL.equalsIgnoreCase(dto.getCaplTransaction().getAmdocsChannelCode());
    }

    /**
     * Verifica si acepto el SatPush Oferta CAPL.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si acepto el SatPush Oferta CAPL, {@code false} en caso contrario.
     */
    public boolean isOfferAccepted(CaplUpsellSaleDTO dto) {
        return Boolean.TRUE.equals(dto.getCaplSale().getSatPushAccepted());
    }

    /**
     * Verifica si el cliente tiene comunicacion post-capl.
     *
     * @param dto el objeto CaplUpsellSaleDTO que contiene la información del cliente
     * @return {@code true} si el cliente tiene comunicacion post-capl, {@code false} en caso contrario.
     */
    public boolean isValidClient(CaplUpsellSaleDTO dto) {
        return Objects.isNull(dto.getCaplSale().getSmsPostCaplContactDate());
    }

}
