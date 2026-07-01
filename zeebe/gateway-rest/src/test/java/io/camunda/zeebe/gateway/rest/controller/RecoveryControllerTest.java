/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(RecoveryController.class)
public class RecoveryControllerTest extends RestControllerTest {

  @MockitoBean ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;

  @ParameterizedTest
  @ValueSource(strings = {"/v2/mode", "/physical-tenants/default/v2/mode"})
  void shouldChangeClusterModeAndReturnPlannedChanges(final String baseUrl) {
    // given
    final var changeResponse =
        new ClusterConfigurationChangeResponse(
            7L,
            Map.of(),
            Map.of(),
            List.of(new ModeChangeOperation(MemberId.from("0"), Mode.RECOVERING)));
    Mockito.when(
            clusterConfigurationRequestSender.modeChange(
                new ModeChangeRequest(Mode.RECOVERING, false)))
        .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

    final var expectedResponse =
        """
        {
          "changeId": "7",
          "plannedChanges": [
            {
              "operation": "ModeChangeOperation",
              "mode": "RECOVERING"
            }
          ]
        }
        """;

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "?mode=RECOVERING&dryRun=false")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/mode", "/physical-tenants/default/v2/mode"})
  void shouldMapErrorResponseWhenModeChangeRejected(final String baseUrl) {
    // given
    Mockito.when(
            clusterConfigurationRequestSender.modeChange(
                new ModeChangeRequest(Mode.RECOVERING, false)))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new ErrorResponse(ErrorCode.CONCURRENT_MODIFICATION, "a change is ongoing"))));

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "?mode=RECOVERING&dryRun=false")
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }
}
