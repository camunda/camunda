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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateAdHocSubProcessActivityRequest;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocSubProcessActivityServicesTest {

  @Mock private BrokerClient brokerClient;
  @Mock private SecurityContextProvider securityContextProvider;

  @InjectMocks private AdHocSubProcessActivityServices adHocSubProcessActivityServices;

  @Test
  void shouldActivateAdHocSubProcessActivities() {
    // given
    final String adHocSubProcessInstanceKey = "123456789";
    final List<AdHocSubProcessActivateActivityReference> elements =
        List.of(
            new AdHocSubProcessActivateActivityReference("element1"),
            new AdHocSubProcessActivateActivityReference("element2"));
    final AdHocSubProcessActivateActivitiesRequest request =
        new AdHocSubProcessActivateActivitiesRequest(adHocSubProcessInstanceKey, elements);

    final AdHocSubProcessActivityActivationRecord expectedResponse =
        new AdHocSubProcessActivityActivationRecord();
    when(brokerClient.sendRequest(any(BrokerActivateAdHocSubProcessActivityRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(expectedResponse));

    // when
    final CompletableFuture<AdHocSubProcessActivityActivationRecord> result =
        adHocSubProcessActivityServices.activateActivities(request);

    // then
    assertThat(result).isCompletedWithValue(expectedResponse);

    final ArgumentCaptor<BrokerActivateAdHocSubProcessActivityRequest> requestCaptor =
        ArgumentCaptor.forClass(BrokerActivateAdHocSubProcessActivityRequest.class);
    verify(brokerClient).sendRequest(requestCaptor.capture());

    final BrokerActivateAdHocSubProcessActivityRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getAdHocSubProcessInstanceKey()).isEqualTo(adHocSubProcessInstanceKey);
    assertThat(capturedRequest.getElements()).containsExactly("element1", "element2");
  }
}