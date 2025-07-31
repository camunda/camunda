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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class AdHocSubProcessActivityServicesTest {

  private AdHocSubProcessActivityServices services;
  private BrokerClient brokerClient;

  @BeforeEach
  public void before() {
    brokerClient = mock(BrokerClient.class);
    final SecurityContextProvider securityContextProvider = mock(SecurityContextProvider.class);
    final CamundaAuthentication authentication = CamundaAuthentication.none();
    services =
        new AdHocSubProcessActivityServices(brokerClient, securityContextProvider, authentication);
  }

  @Test
  public void shouldActivateActivitiesWithEmptyVariables() {
    // given
    final String adHocSubProcessInstanceKey = "123456";
    final String elementId = "activity1";
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(elementId, Map.of());
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, List.of(element));

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    // verify broker request was called correctly
    final ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor =
        ArgumentCaptor.forClass(BrokerActivateAdHocSubProcessActivityRequest.class);
    verify(brokerClient).sendRequest(requestCaptor.capture());

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(adHocSubProcessInstanceKey);
  }

  @Test
  public void shouldActivateActivitiesWithVariables() {
    // given
    final String adHocSubProcessInstanceKey = "123456";
    final String elementId = "activity1";
    final Map<String, Object> variables = Map.of("key1", "value1", "key2", 42);
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(elementId, variables);
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, List.of(element));

    final AdHocSubProcessInstructionRecord expectedResponse =
        mock(AdHocSubProcessInstructionRecord.class);
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    // verify broker request was called correctly
    final ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor =
        ArgumentCaptor.forClass(BrokerActivateAdHocSubProcessActivityRequest.class);
    verify(brokerClient).sendRequest(requestCaptor.capture());

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(adHocSubProcessInstanceKey);
  }

  @Test
  public void shouldActivateMultipleActivities() {
    // given
    final String adHocSubProcessInstanceKey = "123456";
    final AdHocSubProcessActivateActivityReference element1 =
        new AdHocSubProcessActivateActivityReference("activity1", Map.of("var1", "value1"));
    final AdHocSubProcessActivateActivityReference element2 =
        new AdHocSubProcessActivateActivityReference("activity2", Map.of("var2", "value2"));
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, List.of(element1, element2));

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    // verify broker request was called correctly
    final ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor =
        ArgumentCaptor.forClass(BrokerActivateAdHocSubProcessActivityRequest.class);
    verify(brokerClient).sendRequest(requestCaptor.capture());

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(adHocSubProcessInstanceKey);
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
    final String adHocSubProcessInstanceKey = "123456";
    final String elementId = "activity1";
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference(elementId, null);
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, List.of(element));

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    // verify broker request was called correctly
    final ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor =
        ArgumentCaptor.forClass(BrokerActivateAdHocSubProcessActivityRequest.class);
    verify(brokerClient).sendRequest(requestCaptor.capture());

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(adHocSubProcessInstanceKey);
  }

  @Test
  public void shouldReturnNewInstanceWithAuthentication() {
    // given
    final CamundaAuthentication newAuthentication = mock(CamundaAuthentication.class);

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
    final String adHocSubProcessInstanceKey = "123456";
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, List.of());

    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result = services.activateActivities(request).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);

    // verify broker request was called correctly
    final ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor =
        ArgumentCaptor.forClass(BrokerActivateAdHocSubProcessActivityRequest.class);
    verify(brokerClient).sendRequest(requestCaptor.capture());

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(adHocSubProcessInstanceKey);
    assertThat(capturedRequest.getRequestWriter().getActivateElements()).isEmpty();
  }

  @Test
  public void shouldPropagateCompletableFutureFromBrokerClient() {
    // given
    final String adHocSubProcessInstanceKey = "123456";
    final AdHocSubProcessActivateActivityReference element =
        new AdHocSubProcessActivateActivityReference("activity1", Map.of());
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, List.of(element));

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
}
