package com.tdp.ms.caplupsellsale;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.tdp.ms.caplupsellsale.builder.KStreamBuilder;
import com.tdp.ms.caplupsellsale.pojo.*;
import com.tdp.ms.caplupsellsale.service.SendingFeedbackService;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.tdp.ms.caplupsellsale.business.Filters;
import com.tdp.ms.caplupsellsale.business.Maps;
import com.tdp.ms.caplupsellsale.commons.Constant;
import com.tdp.ms.caplupsellsale.commons.EventLogger;
import com.tdp.ms.caplupsellsale.commons.Utils;
import com.tdp.ms.caplupsellsale.dto.CaplUpsellSaleDTO;
import com.tdp.ms.caplupsellsale.service.BranchConditional;

/**
 * La campaña de SatPush Venta de Capl Upsell Masivo esta dirigida a clientes que estan por agotar o agotan el plan de datos donde se
 * les envía 2 SatPush seguidos(SatPush Doble Optin) con oferta capl, si el cliente acepta ambos SatPush se le aprovisiona un bono y se
 * le envia un SMS. También se le notifica al cliente cuando ya tiene el cambio de plan efectivo. Otro proceso generara un reporte con
 * todas los satpush aceptados(ventas aceptadas) y otro equipo se encargara de realizar el Capl con este reporte.
 *
 *
 * @author: Elizabeth Valdez Pacheco
 * @version 0.1 - Released in December 2023
 */
@SpringBootApplication
public class CaplUpsellSaleStreamApplication {

    /**
     * Lee del topico streaming-notifyexternalsup-campaign, procesa, filtra y arma el request de SatPush y lo envia
     * topico queue-satpush-api. Este proceso se encarga de enviar los SatPush con la oferta, se aprovisionara el bono
     * si el cliente acepta.
     *
     * @param logger EventLogger
     * @param filters Filters
     * @param maps Maps
     */
    @Bean
    public Function<KStream<String, NotifyExternalSupRequest>, KStream<String, String>> firstProcess(EventLogger logger,
                                                                                                     Filters filters, Maps maps, BranchConditional branchConditional) {
        return input -> {
            Map<String, KStream<String, CaplUpsellSaleDTO>> eventType = input
                    .mapValues(logger::generateTrackingAndLogInput)
                    .mapValues(maps::fromNotifyExternalSupToDTO)
                    .filter((key, value) -> filters.isInWhiteList(value))
                    .mapValues(maps::setDtoPromotion)
                    .filter((key, value) -> filters.isPromotionPresent(value))
                    .filter((key, value) -> filters.isValidDatePromotion(value))
                    .filter((key, value) -> filters.isValidOperationCode(value))
                    .filter((key, value) -> filters.isInBoltonList(value))
                    .mapValues(maps::setDtoSubscribers)
                    .filter((key, value) -> filters.isSubscribersPresent(value))
                    .filter((key, value) -> filters.isNotLMAPlan(value))
                    .filter((key, value) -> filters.isResidential(value))
                    .filter((key, value) -> filters.isNotMovistarTotal(value))
                    .filter((key, value) -> filters.isNotBlackList(value))
                    .filter((key, value) -> filters.isValidProductType(value))
                    .mapValues(maps::setDtoValidationDebts)
                    .filter((key, value) -> filters.hasNoDebtAnyMonth(value))
                    .filter((key, value) -> filters.hasNoDebtM3(value))
                    .filter((key, value) -> filters.hasNoDebtM2M3(value))
                    .filter((key, value) -> filters.hasNoDebtM2(value))
                    .filter((key, value) -> filters.hasPaidSomeReceipts(value))
                    .filter((key, value) -> filters.isNotInCaplRequest(value))
                    .mapValues(maps::setDtoPlanCatalog)
                    .filter((key, value) -> filters.isPlanCatalogPresent(value))
                    .filter((key, value) -> filters.isFixedChargeOriginGreaterThanZero(value))
                    .mapValues(maps::setDtoPlanChangeOfferByOriginFC)
                    .filter((key, value) -> filters.isPlanChangeOfferPresent(value))
                    .mapValues((key, value) -> maps.setDtoPlanChangeSale(value))
                    .mapValues(maps::setDtoSatPushTenor)
                    .filter((key, value) -> filters.isSatPushTenorPresent(value))
                    .split(Named.as(Constant.BRANCH_INIT))
                    .branch((key, value) -> branchConditional.isThresholdOperationCode(value), Branched.as(Constant.THRESHOLD))
                    .defaultBranch(Branched.as(Constant.EXHAUSTION));

            KStream<String, CaplUpsellSaleDTO> thresholdEvent = eventType.get(Constant.BRANCH_THRESHOLD)
                    .filter((key, value) -> filters.isNotInPlanChangeSale(value))
                    .filter((key, value) -> filters.isOfferNotAccepted(value))
                    .peek((key, value) -> maps.saveCAPLPreSaleInCache(value))
                    .peek((key, value) -> maps.saveCAPLPreSale(value))
                    .mapValues(maps::setDtoSatPushThresholdDetail);

            KStream<String, CaplUpsellSaleDTO> exhaustionEvent = eventType.get(Constant.BRANCH_EXHAUSTION)
                    .filter((key, value) -> filters.isCaplSalePresent(value))
                    .filter((key, value) -> filters.isThresholdPresent(value))
                    .filter((key, value) -> filters.isOfferNotAccepted(value))
                    .peek((key, value) -> maps.saveCAPLPreSaleInCache(value))
                    .peek((key, value) -> maps.saveCAPLPreSale(value))
                    .mapValues(maps::setDtoSatPushExhaustionDetail);

            return thresholdEvent.merge(exhaustionEvent)
                    .mapValues(maps::setDtoSatPushDescriptions)
                    .peek((key, value) -> maps.saveCustomerContact(value, Constant.SATPUSH))
                    .mapValues(maps::buildSatPushRequest)
                    .mapValues(Utils::toJson)
                    .mapValues(logger::logOutput);

        };
    }

    /**
     * Lee del topico streaming-sendingfeedback-campaign, procesa, filtra y arma el request de sms y lo envia topico
     * queue-sms-api. Este proceso lee todas las aceptaciones del satpush ofertas y envia un sms.
     *
     * @param logger EventLogger
     * @param filters Filters
     * @param maps Maps
     */
    @Bean
    public Consumer<KStream<String, SendingFeedbackRequest>> secondProcess(EventLogger logger,
                                                                           Filters filters, Maps maps, SendingFeedbackService sendingFeedbackService
    ) {
        return input -> {
            List<KStream<String, CaplUpsellSaleDTO>> convergence = new ArrayList<>();

            KStreamBuilder<String, SendingFeedbackRequest> builderIn = new KStreamBuilder<>();
            builderIn.input(input)
                    .mapValues(logger::generateTrackingAndLogInput)
                    .mapValues(maps::fromSendingFeedbackToDTO)
                    .filter((key, value) -> filters.isValidCampaignId(value))
                    .mapValues(maps::setDtoPromotion)
                    .filter((key, value) -> filters.isPromotionPresent(value))
                    .filter((key, value) -> filters.isValidDatePromotion(value))
                    .mapValues(maps::setDtoPlanChangeSaleByFeedback)
                    .filter((key, value) -> filters.isPlanChangeSalePresent(value))
                    .peek((key, value) -> maps.updateCAPLSaleInCache(value))
                    .peek((key, value) -> maps.updateCAPLSale(value))
                    .mapValues(maps::setDtoBillingCycleLogic)
                    .filter((key, value) -> filters.isBillingCycleLogicPresent(value))
                    .mapValues(maps::setDtoActivationDateTenor)
                    .filter((key, value) -> filters.isActivationDateTenorPresent(value))
                    .mapValues(maps::setDtoPlanNameTenor)
                    .filter((key, value) -> filters.isPlanNameTenorPresent(value))
                    .mapValues(maps::setDtoSmsAcceptanceTenor)
                    .filter((key, value) -> filters.isSmsAcceptanceTenorPresent(value))
                    .mapValues(maps::setDtoAcceptanceSmsDetail)
                    .mapValues(maps::setDtoSmsAcceptanceDescriptions)
                    .peek((key, value) -> maps.saveCustomerContact(value, Constant.SMS))
                    .saveKStreamIn(convergence);

            processSendingCascade(convergence, maps, sendingFeedbackService, logger);
        };
    }


    private void processSendingCascade(
            List<KStream<String, CaplUpsellSaleDTO>> input, Maps maps,
            SendingFeedbackService sendingFeedbackService, EventLogger logger
    ) {
        KStreamBuilder<String, CaplUpsellSaleDTO> builder = new KStreamBuilder<>();

        builder.mergeAll(input)
                //verificar si el 1er SMS esta activo
                .peek((key, value) -> {
                    try {
                        maps.sendSmsNotification(value, Constant.SMS);
                        maps.updateSendSMSInCache(value, Constant.SMS);  // flagSms1 = true
                    } catch (Exception e) {
                        logger.logError("Error enviando SMS1", e);
                    }
                })
                //.filter((key, value) -> maps.hasSmsAcceptanceFlag(value))  // Verificar flagSMS1 = 1
                //verificar si el 2do SMS esta activo
                //verificar si el tipo de plan es control e ilimitado para enviar el SMS
                .peek((key, value) -> {
                    try {
                        maps.sendSmsNotification(value, Constant.SMS2);
                        maps.updateSendSMSInCache(value, Constant.SMS2);  // flagSms2 = true
                    } catch (Exception e) {
                        logger.logError("Error enviando SMS2", e);
                    }
                })
                .mapValues(Utils::toJson)
                .mapValues(logger::logOutput)
                .toKStream();
    }

    /**
     * Lee del topico queue-capl-transaction, procesa, filtra y arma el request de sms y lo envia topico
     * queue-sms-api. Este proceso lee las confirmaciones de cambio de plan al D+1 de su ciclo de facturación.
     *
     * @param logger EventLogger
     * @param filters Filters
     * @param maps Maps
     */
    @Bean
    public Function<KStream<String, CaplTransaction>, KStream<String, String>> thirdProcess(EventLogger logger,
                                                                                            Filters filters, Maps maps) {
        return input -> input.mapValues(logger::generateTrackingAndLogInput)
                .mapValues(maps::fromCaplTransactionToDTO)
                .filter((key, value) -> filters.isInWhiteList(value))
                .mapValues(maps::setDtoPromotion)
                .filter((key, value) -> filters.isPromotionPresent(value))
                .filter((key, value) -> filters.isValidDatePromotion(value))
                .filter((key, value) -> filters.isCaplTransaction(value))
                .filter((key, value) -> filters.isClosedTransaction(value))
                .filter((key, value) -> filters.isSubscriberCdPresent(value))
                .mapValues(maps::setDtoPlanChangeSale)
                .filter((key, value) -> filters.isPlanChangeSalePresent(value))
                .filter((key, value) -> filters.isOfferAccepted(value))
                .filter((key, value) -> filters.isValidClient(value))
                .filter((key, value) -> filters.isValidSourceFixedCharge(value))
                .filter((key, value) -> filters.isValidSourcePlanId(value))
                .filter((key, value) -> filters.isValidDestFixedCharge(value))
                .filter((key, value) -> filters.isMassiveChannel(value))
                .mapValues(maps::setDtoPlanNameTenor)
                .filter((key, value) -> filters.isPlanNameTenorPresent(value))
                .mapValues(maps::setDtoSmsCaplTenor)
                .filter((key, value) -> filters.isCaplSmsTenorPresent(value))
                .mapValues(maps::setDtoSmsCaplDescriptions)
                .peek((key, value) -> maps.updateCAPLPostSaleInCache(value))
                .peek((key, value) -> maps.updateCAPLPostSale(value))
                .peek((key, value) -> maps.saveCustomerContact(value, Constant.SMS))
                .mapValues(value -> maps.buildSmsRequest(value, Constant.SMS3))
                .mapValues(Utils::toJson)
                .mapValues(logger::logOutput);
    }

    /**
     * Lee del topico queue-capl-main-customer, procesa, filtra y arma el request de sms y lo envia topico
     * queue-satpush-api. Este proceso lee eventos cargados manualmente por el usuario y notifica a los clientes con ofertas capl.
     *
     * @param logger EventLogger
     * @param filters Filters
     * @param maps Maps
     */
    @Bean
    public Function<KStream<String, CaplMainCustomer>, KStream<String, String>> fourthProcess(EventLogger logger,
                                                                                              Filters filters, Maps maps) {

        return input -> input.mapValues(logger::generateTrackingAndLogInput)
                .mapValues(maps::fromCaplMainCustomerDTO)
                .mapValues(maps::setDtoPromotion)
                .filter((key, value) -> filters.isPromotionPresent(value))
                .filter((key, value) -> filters.isValidDatePromotion(value))
                .filter((key, value) -> filters.isNotInContactability(value))
                .mapValues(maps::setDtoSubscribers)
                .filter((key, value) -> filters.isSubscribersPresent(value))
                .filter((key, value) -> filters.isNotLMAPlan(value))
                .filter((key, value) -> filters.isResidential(value))
                .filter((key, value) -> filters.isNotMovistarTotal(value))
                .filter((key, value) -> filters.isNotInCaplRequest(value))
                .filter((key, value) -> filters.isValidProductType(value))
                .filter((key, value) -> filters.isValidOfferType(value))
                .filter((key, value) -> filters.isNotBlackList(value))
                .mapValues(maps::setDtoPlanCatalog)
                .filter((key, value) -> filters.isValidOriginFixedCharge(value))
                .filter((key, value) -> filters.isValidNetOriginFixedCharge(value))
                .mapValues(maps::setDtoFixedCharges)
                .filter((key, value) -> filters.isFixedChargeOfferGreaterThanZero(value))
                .mapValues(maps::setDtoPlanChangeOfferByFixedCharge)
                .filter((key, value) -> filters.isPlanChangeOfferPresent(value))
                .filter((key, value) -> filters.isFixedChargeDiffGreaterThanZero(value))
                .filter((key, value) -> filters.isGigabytesAmountDiffGreaterThanZero(value))
                .mapValues((key, value) -> maps.setDtoPlanChangeSale(value))
                .filter((key, value) -> filters.isOfferNotAccepted(value))
                .peek((key, value) -> maps.saveCAPLPreSaleInCache(value))
                .peek((key, value) -> maps.saveCAPLPreSale(value))
                .mapValues(maps::setDtoSatPushTenor)
                .filter((key, value) -> filters.isSatPushTenorPresent(value))
                .mapValues(maps::setDtoSatPushManualFlowDetail)
                .mapValues(maps::setDtoSatPushDescriptions)
                .peek((key, value) -> maps.updateCustomerStatus(value))
                .peek((key, value) -> maps.saveContactability(value))
                .peek((key, value) -> maps.saveCustomerContact(value, Constant.SATPUSH))
                .mapValues(maps::buildSatPushRequest)
                .mapValues(Utils::toJson)
                .mapValues(logger::logOutput);
    }

    public static void main(String[] args) {
        SpringApplication.run(CaplUpsellSaleStreamApplication.class, args);
    }

}
