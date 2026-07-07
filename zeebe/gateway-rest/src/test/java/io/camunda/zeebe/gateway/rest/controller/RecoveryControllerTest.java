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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
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

  private void stubValidationSuccess() {
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new ClusterConfigurationChangeResponse(0L, Map.of(), Map.of(), List.of()))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldForwardBackupIdsRestoreAndReturnAccepted(final String baseUrl) {
    // given
    stubValidationSuccess();

    // when / then
    webClient
        .post()
        .uri(baseUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100, 101]}")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .json("{\"changeId\": \"0\", \"plannedChanges\": []}", JsonCompareMode.STRICT);

    // then
    Mockito.verify(clusterConfigurationRequestSender)
        .restore(new RestoreRequest(List.of(100L, 101L), null, null, false, false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldForwardTimeRangeRestoreAndReturnAccepted(final String baseUrl) {
    // given
    stubValidationSuccess();

    // when / then
    webClient
        .post()
        .uri(baseUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"from\": \"2024-01-01T10:00:00Z\", \"to\": \"2024-01-01T12:00:00Z\"}")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectBody()
        .json("{\"changeId\": \"0\", \"plannedChanges\": []}", JsonCompareMode.STRICT);

    // then
    Mockito.verify(clusterConfigurationRequestSender)
        .restore(
            new RestoreRequest(
                List.of(), "2024-01-01T10:00:00Z", "2024-01-01T12:00:00Z", false, false));
  }

  @Test
  void shouldMapInvalidRequestErrorFromCoordinator() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INVALID_REQUEST, "bad params"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100], \"from\": \"2024-01-01T10:00:00Z\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldMapOperationNotAllowedErrorWhenClusterNotRecovering() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.OPERATION_NOT_ALLOWED, "not recovering"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void shouldMapInternalErrorFromCoordinator() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INTERNAL_ERROR, "boom"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void shouldMapConcurrentModificationFromCoordinator() {
    // given
    Mockito.when(clusterConfigurationRequestSender.restore(Mockito.any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.CONCURRENT_MODIFICATION, "boom"))));

    // when / then
    webClient
        .post()
        .uri("/v2/restore")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"backupIds\": [100]}")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
  void shouldAllowNoParameter(final String baseUrl) {
    // given
    stubValidationSuccess();

    // when / then
    webClient
        .post()
        .uri(baseUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isAccepted();

    // then
    Mockito.verify(clusterConfigurationRequestSender)
        .restore(new RestoreRequest(List.of(), null, null, false, false));
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.primary-storage.backup.continuous=true"})
  class RestoreWithContinuousBackups {

    @ParameterizedTest
    @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
    void shouldForwardContinuousFlagWithBackupIds(final String baseUrl) {
      // given
      stubValidationSuccess();

      // when / then
      webClient
          .post()
          .uri(baseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{\"backupIds\": [100, 101]}")
          .exchange()
          .expectStatus()
          .isAccepted();

      // then
      Mockito.verify(clusterConfigurationRequestSender)
          .restore(new RestoreRequest(List.of(100L, 101L), null, null, true, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v2/restore", "/physical-tenants/default/v2/restore"})
    void shouldForwardContinuousFlagWithTimeRange(final String baseUrl) {
      // given
      stubValidationSuccess();

      // when / then
      webClient
          .post()
          .uri(baseUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{\"from\": \"2024-01-01T10:00:00Z\", \"to\": \"2024-01-01T12:00:00Z\"}")
          .exchange()
          .expectStatus()
          .isAccepted();

      // then
      Mockito.verify(clusterConfigurationRequestSender)
          .restore(
              new RestoreRequest(
                  List.of(), "2024-01-01T10:00:00Z", "2024-01-01T12:00:00Z", true, false));
    }
  }
}
