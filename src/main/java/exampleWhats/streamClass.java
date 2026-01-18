package com.telefonica.pcr;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.kafka.streams.kstream.KStream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.telefonica.pcr.builder.KStreamBuilder;
import com.telefonica.pcr.business.Filters;
import com.telefonica.pcr.business.Maps;
import com.telefonica.pcr.commons.EventLogger;
import com.telefonica.pcr.commons.Utils;
import com.telefonica.pcr.dto.CallRetentionDTO;
import com.telefonica.pcr.pojo.CallRivalsRetention;

@SpringBootApplication
public class ProactiveCallRetentionStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProactiveCallRetentionStreamApplication.class, args);
    }

    @Bean
    public Consumer<KStream<String, CallRivalsRetention>> process(Filters filters, Maps maps, EventLogger logger) {
        return input -> {

            KStreamBuilder<String, CallRivalsRetention> builderIn = new KStreamBuilder<>();
            List<KStream<String, CallRetentionDTO>> convergence = new ArrayList<>();

            builderIn.input(input)
                    .mapValues(logger::generateTrackingAndLogInput)
                    .mapValues(maps::fromCallRivalsRetentionToDto)
                    .filter((key, value) -> filters.isCorrectData(value))
                    .filter((key, value) -> filters.isInTheWhiteList(value))
                    .filter((key, value) -> filters.isNotWebTraffic(value))
                    .filter((key, value) -> filters.isNotFixedTraffic(value))
                    .filter((key, value) -> filters.isLastHoursTraffic(value))
                    .filter((key, value) -> filters.isAllowedExternalOperator(value))
                    .mapValues(maps::setThresholdParams)
                    .filter((key, value) -> filters.isThresholdParamPresent(value))
                    .filter((key, value) -> filters.hasNotCP(value))
                    .filter((key, value) -> filters.wasNotApproachedByTR(value))
                    .filter((key, value) -> filters.isTheDurationInTheRange(value))
                    .filter((key, value) -> filters.isMobileOrWeb(value))
                    .mapValues(maps::setSubscriberInfo)
                    .filter((key, value) -> filters.isNotSubscriberEmpty(value))
                    .filter((key, value) -> filters.isNotDocumentEmpty(value))
                    .filter((key, value) -> filters.isNotInContactability(value))
                    .filter((key, value) -> filters.isPostpaid(value))
                    .filter((key, value) -> filters.isResidential(value))
                    .filter((key, value) -> filters.hasNotFamilyPlan(value))
                    .mapValues(maps::setClusterGroupVariables)
                    .filter((key, value) -> filters.isClusterGroupVariablesPresent(value))
                    .mapValues(maps::setClusterGroupTag)
                    .filter((key, value) -> filters.isClusterGroupTagPresent(value))
                    .mapValues(maps::setTransversalGroupTag)
                    .filter((key, value) -> filters.isProductTagPresent(value))
                    .mapValues(maps::setDiscountTag)
                    .filter((key, value) -> filters.isDiscountTagPresent(value))
                    .mapValues(maps::setTargetCluster)
                    .filter((key, value) -> filters.isTargetClusterPresent(value))
                    .mapValues(maps::setGroupTag)
                    .filter((key, value) -> filters.isGroupTagPresent(value))
                    .mapValues(maps::setClusterOffer)
                    .filter((key, value) -> filters.isClusterOfferPresent(value))
                    .mapValues(maps::setPlanesCatalog)
                    .filter((key, value) -> filters.isPlanCatalogPresent(value))
                    .mapValues(maps::assignFixedCharge)
                    .mapValues(maps::setCalculator)
                    .mapValues(maps::setSegment)
                    .saveKStreamIn(convergence);

            processSendWhatsAppMessage(convergence, filters, maps);
            processSendToCallCenterAndSatPush(convergence, filters, maps, logger);
        };
    }

    private void processSendWhatsAppMessage(
            List<KStream<String, CallRetentionDTO>> input, Filters filters, Maps maps
    ) {
        KStreamBuilder<String, CallRetentionDTO> builder = new KStreamBuilder<>();

        builder.mergeAll(input)
                .mapValues(maps::setActivateSending)
                .filter((key, value) -> filters.isActiveSending(value))
                .mapValues(maps::setCustomerNotiConfig)
                .filter((key, value) -> filters.isCustomerNotiConfigPresent(value))
                .mapValues(maps::setCustomerNotiTemplate)
                .filter((key, value) -> filters.isCustomerNotiTemplatePresent(value))
                .peek((key, value) -> maps.sendWhatsAppNotification(value));
    }

    private void processSendToCallCenterAndSatPush(
            List<KStream<String, CallRetentionDTO>> input, Filters filters, Maps maps, EventLogger logger
    ) {
        KStreamBuilder<String, CallRetentionDTO> builder = new KStreamBuilder<>();

        builder.mergeAll(input)
                .peek((key, value) -> maps.saveContactability(value))
                .peek((key, value) -> maps.saveCustomerContact(value))
                .peek((key, value) -> maps.sendSatPush(value))
                .mapValues(maps::sendLeadToThirdParty)
                .peek((key, value) -> maps.saveLeadInfo(value))
                .filter((key, value) -> filters.isSatisfactoryResponse(value))
                .mapValues(Utils::toJson)
                .mapValues(logger::logOutput);
    }
}