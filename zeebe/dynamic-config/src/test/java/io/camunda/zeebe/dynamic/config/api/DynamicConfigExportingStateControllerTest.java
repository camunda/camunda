/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

final class DynamicConfigExportingStateControllerTest {

  private final ClusterConfigurationManagementRequestSender requestSender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final DynamicConfigExportingStateController controller =
      new DynamicConfigExportingStateController(
          requestSender, Duration.ofMillis(1), Duration.ofSeconds(10));

  @ParameterizedTest
  @MethodSource("operations")
  void shouldSubmitExpectedTargetState(
      final Function<DynamicConfigExportingStateController, CompletableFuture<Void>> operation,
      final ExportingState expectedState) {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    operation.apply(controller).join();

    // then
    assertThat(captor.getValue().state()).isEqualTo(expectedState);
  }

  @ParameterizedTest
  @MethodSource("operations")
  void shouldFailIfSubmissionIsRejected(
      final Function<DynamicConfigExportingStateController, CompletableFuture<Void>> operation,
      final ExportingState expectedState) {
    // given
    when(requestSender.changeExportingState(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorResponse.ErrorCode.INVALID_REQUEST, "nope"))));

    // when - then
    assertThatThrownBy(() -> operation.apply(controller).join()).hasMessageContaining("nope");
  }

  private static Stream<Arguments> operations() {
    return Stream.of(
        Arguments.of(
            (Function<DynamicConfigExportingStateController, CompletableFuture<Void>>)
                c -> c.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID),
            ExportingState.PAUSED),
        Arguments.of(
            (Function<DynamicConfigExportingStateController, CompletableFuture<Void>>)
                c -> c.softPauseExporting(DEFAULT_PHYSICAL_TENANT_ID),
            ExportingState.SOFT_PAUSED),
        Arguments.of(
            (Function<DynamicConfigExportingStateController, CompletableFuture<Void>>)
                c -> c.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID),
            ExportingState.EXPORTING));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> emptyPlan() {
    return CompletableFuture.completedFuture(
        Either.right(new ClusterConfigurationChangeResponse(0, Map.of(), Map.of(), List.of())));
  }
}
