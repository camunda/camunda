/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateAdHocSubProcessActivityRequest;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue.AdHocSubProcessActivateElementInstructionValue;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AdHocSubProcessActivityServicesTest {

  private static final String AD_HOC_SUB_PROCESS_INSTANCE_KEY = "123456";
  private static final String ELEMENT_ID = "activity1";

  private AdHocSubProcessActivityServices services;

  @Mock private BrokerClient brokerClient;

  @Mock private SecurityContextProvider securityContextProvider;

  @Captor private ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor;

  @BeforeEach
  public void before() {
    final CamundaAuthentication authentication = CamundaAuthentication.none();
    services =
        new AdHocSubProcessActivityServices(brokerClient, securityContextProvider, authentication);
  }

  @Test
  public void shouldActivateActivitiesWithEmptyVariables() {
    // given
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(ELEMENT_ID, Map.of());
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(element), true);

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
  }

  @Test
  public void shouldActivateActivitiesWithVariables() {
    // given
    final Map<String, Object> variables = Map.of("key1", "value1", "key2", 42);
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(ELEMENT_ID, variables);
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(element), false);

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
  }

  @Test
  public void shouldActivateMultipleActivities() {
    // given
    final AdHocSubProcessActivateActivityReference element1 =
        new AdHocSubProcessActivateActivityReference("activity1", Map.of("var1", "value1"));
    final AdHocSubProcessActivateActivityReference element2 =
        new AdHocSubProcessActivateActivityReference("activity2", Map.of("var2", "value2"));
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(element1, element2), true);

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
    assertThat(capturedRequest.getRequestWriter().getActivateElements()).hasSize(2);
    assertThat(capturedRequest.getRequestWriter().getActivateElements())
        .filteredOn(AdHocSubProcessActivateElementInstructionValue::getElementId, "activity1")
        .extracting(AdHocSubProcessActivateElementInstructionValue::getVariables)
        .containsExactly(Map.of("var1", "value1"));
    assertThat(capturedRequest.getRequestWriter().getActivateElements())
        .filteredOn(AdHocSubProcessActivateElementInstructionValue::getElementId, "activity2")
        .extracting(AdHocSubProcessActivateElementInstructionValue::getVariables)
        .containsExactly(Map.of("var2", "value2"));
  }

  @Test
  public void shouldActivateActivitiesWithNullVariables() {
    // given
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(ELEMENT_ID, null);
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(element), false);

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
  }

  @Test
  public void shouldReturnNewInstanceWithAuthentication() {
    // given
    final CamundaAuthentication newAuthentication = CamundaAuthentication.none();

    // when
    final AdHocSubProcessActivityServices newServices =
        services.withAuthentication(newAuthentication);

    // then
    assertThat(newServices).isNotSameAs(services);
    assertThat(newServices).isInstanceOf(AdHocSubProcessActivityServices.class);
  }

  @Test
  public void shouldHandleEmptyElementsList() {
    // given
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(), true);

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
    assertThat(capturedRequest.getRequestWriter().getActivateElements()).isEmpty();
  }

  @Test
  public void shouldPropagateCompletableFutureFromBrokerClient() {
    // given
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference("activity1", Map.of());
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(element), true);

    final CompletableFuture<BrokerResponse<AdHocSubProcessInstructionRecord>> brokerFuture =
        new CompletableFuture<>();

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(brokerFuture);

    // when
    final CompletableFuture<AdHocSubProcessInstructionRecord> result =
        services.activateActivities(request);

    // then
    assertThat(result).isNotCompleted();

    // complete the broker future
    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);
    brokerFuture.complete(brokerResponse);

    assertThat(result.join()).isEqualTo(expectedResponse);
  }

  @ParameterizedTest
  @MethodSource("cancelRemainingInstancesProvider")
  public void shouldSetCancelRemainingInstances(Boolean cancelRemainingInstances) {
    // given
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(ELEMENT_ID, Map.of());
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY, List.of(element), cancelRemainingInstances);

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
    assertThat(capturedRequest.getRequestWriter().isCancelRemainingInstances())
        .isEqualTo(cancelRemainingInstances != null ? cancelRemainingInstances : false);
  }

  private static Stream<Arguments> cancelRemainingInstancesProvider() {
    return Stream.of(Arguments.of(true), Arguments.of(false), Arguments.of((Boolean) null));
  }
}
