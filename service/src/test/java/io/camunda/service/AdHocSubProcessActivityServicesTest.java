/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AdHocSubProcessActivityServicesTest {

  private static final long AD_HOC_SUB_PROCESS_INSTANCE_KEY = 123456L;
  private static final String ELEMENT_ID = "activity1";

  private static final AdHocSubProcessActivateActivitiesRequest DEFAULT_ACTIVATION_REQUEST =
      new AdHocSubProcessActivateActivitiesRequest(
          AD_HOC_SUB_PROCESS_INSTANCE_KEY,
          List.of(new AdHocSubProcessActivateActivityReference(ELEMENT_ID, Map.of())),
          false);
  private static final BrokerResponse<AdHocSubProcessInstructionRecord> DEFAULT_BROKER_RESPONSE =
      new BrokerResponse<>(new AdHocSubProcessInstructionRecord());

  @Mock private BrokerClient brokerClient;
  @Mock private SecurityContextProvider securityContextProvider;
  @Captor private ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor;

  private AdHocSubProcessActivityServices services;

  @BeforeEach
  public void before() {
    final CamundaAuthentication authentication =
        CamundaAuthentication.of(b -> b.claims(Map.of("claim", "value")));
    services =
        new AdHocSubProcessActivityServices(brokerClient, securityContextProvider, authentication);
  }

  @Test
  public void shouldPropagateCompletableFutureFromBrokerClient() {
    // given
    final CompletableFuture<BrokerResponse<AdHocSubProcessInstructionRecord>> brokerFuture =
        new CompletableFuture<>();

    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(brokerFuture);

    // when
    final CompletableFuture<AdHocSubProcessInstructionRecord> result =
        services.activateActivities(DEFAULT_ACTIVATION_REQUEST);

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

  @Test
  public void shouldActivateActivitiesWithIncludingAuthenticationClaims() {
    // given
    final AdHocSubProcessInstructionRecord expectedResponse =
        new AdHocSubProcessInstructionRecord();
    final BrokerResponse<AdHocSubProcessInstructionRecord> brokerResponse =
        new BrokerResponse<>(expectedResponse);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(brokerResponse));

    // when
    final AdHocSubProcessInstructionRecord result =
        services.activateActivities(DEFAULT_ACTIVATION_REQUEST).join();

    // then
    assertThat(result).isEqualTo(expectedResponse);
    assertThat(requestCaptor.getValue().getAuthorization().getClaims())
        .containsExactly(entry("claim", "value"));
  }

  @Test
  public void shouldReturnNewServiceInstanceWithUpdatedAuthentication() {
    // given
    final CamundaAuthentication newAuthentication =
        CamundaAuthentication.of(b -> b.claims(Map.of("newClaim", "newValue")));

    // when
    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(DEFAULT_BROKER_RESPONSE));

    final AdHocSubProcessActivityServices newServices =
        services.withAuthentication(newAuthentication);

    newServices.activateActivities(DEFAULT_ACTIVATION_REQUEST).join();

    // then
    assertThat(newServices).isNotSameAs(services);
    assertThat(requestCaptor.getValue().getAuthorization().getClaims())
        .containsExactly(entry("newClaim", "newValue"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldActivateActivities(final boolean cancelRemainingInstances) {
    // given
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY,
            List.of(
                new AdHocSubProcessActivateActivityReference("activity1", Map.of()),
                new AdHocSubProcessActivateActivityReference("activity2", Map.of("var", "value"))),
            cancelRemainingInstances);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(DEFAULT_BROKER_RESPONSE));

    // when
    services.activateActivities(request).join();

    // then
    final var instruction = requestCaptor.getValue().getRequestWriter();
    assertThat(instruction.getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
    assertThat(instruction.isCancelRemainingInstances()).isEqualTo(cancelRemainingInstances);
    assertThat(instruction.getActivateElements())
        .satisfiesExactly(
            element -> {
              assertThat(element.getElementId()).isEqualTo("activity1");
              assertThat(element.getVariables()).isEmpty();
            },
            element -> {
              assertThat(element.getElementId()).isEqualTo("activity2");
              assertThat(element.getVariables()).containsExactlyEntriesOf(Map.of("var", "value"));
            });
  }

  @Test
  public void shouldActivateActivitiesWithNullVariables() {
    // given
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(
            AD_HOC_SUB_PROCESS_INSTANCE_KEY,
            List.of(new AdHocSubProcessActivateActivityReference(ELEMENT_ID, null)),
            false);

    when(brokerClient.sendRequest(requestCaptor.capture()))
        .thenReturn(CompletableFuture.completedFuture(DEFAULT_BROKER_RESPONSE));

    // when
    services.activateActivities(request).join();

    // then
    final var instruction = requestCaptor.getValue().getRequestWriter();
    assertThat(instruction.getAdHocSubProcessInstanceKey())
        .isEqualTo(AD_HOC_SUB_PROCESS_INSTANCE_KEY);
    assertThat(instruction.getActivateElements())
        .satisfiesExactly(
            element -> {
              assertThat(element.getElementId()).isEqualTo(ELEMENT_ID);
              assertThat(element.getVariables()).isEmpty();
            });
  }
}
