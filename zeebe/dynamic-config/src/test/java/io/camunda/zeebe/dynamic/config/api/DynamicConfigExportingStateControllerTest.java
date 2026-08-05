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

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

final class DynamicConfigExportingStateControllerTest {

  private final ClusterConfigurationManagementRequestSender requestSender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ExportingStateController.ByTenant controller =
      new DynamicConfigExportingStateController(
              requestSender, Duration.ofMillis(1), Duration.ofSeconds(10))
          .getByTenant(DEFAULT_PHYSICAL_TENANT_ID);

  @ParameterizedTest
  @MethodSource("operations")
  void shouldSubmitExpectedTargetState(
      final Function<ExportingStateController.ByTenant, CompletableFuture<Void>> operation,
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
      final Function<ExportingStateController.ByTenant, CompletableFuture<Void>> operation,
      final ExportingState expectedState) {
    // given
    when(requestSender.changeExportingState(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorResponse.ErrorCode.INVALID_REQUEST, "nope"))));

    // when - then
    assertThatThrownBy(() -> operation.apply(controller).join()).hasMessageContaining("nope");
  }

  @Test
  void shouldAggregateStatusWhenReplicasAgree() {
    // given
    when(requestSender.getTopology())
        .thenReturn(topology(Map.of(MemberId.from("0"), ExportingState.SOFT_PAUSED)));

    // when
    final var status = controller.getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.SOFT_PAUSED);
  }

  @Test
  void shouldReportMixedStatusWhenReplicasDisagree() {
    // given
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    MemberId.from("0"), ExportingState.PAUSED,
                    MemberId.from("1"), ExportingState.EXPORTING)));

    // when
    final var status = controller.getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldReportExportingStatusWhenNeverControlledByConfig() {
    // given - a replica config never touched by a state-change operation is UNKNOWN
    when(requestSender.getTopology())
        .thenReturn(topology(Map.of(MemberId.from("0"), ExportingState.UNKNOWN)));

    // when
    final var status = controller.getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.EXPORTING);
  }

  private static CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> topology(
      final Map<MemberId, ExportingState> statesByMember) {
    final Map<MemberId, MemberState> members =
        statesByMember.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        MemberState.initializeAsActive(
                            Map.of(
                                1,
                                PartitionState.active(
                                    1,
                                    DynamicPartitionConfig.init()
                                        .updateExporting(
                                            config -> config.withState(entry.getValue())))))));
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfiguration(
                1,
                members,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty())));
  }

  private static Stream<Arguments> operations() {
    return Stream.of(
        Arguments.of(
            (Function<ExportingStateController.ByTenant, CompletableFuture<Void>>)
                ExportingStateController.ByTenant::pauseExporting,
            ExportingState.PAUSED),
        Arguments.of(
            (Function<ExportingStateController.ByTenant, CompletableFuture<Void>>)
                ExportingStateController.ByTenant::softPauseExporting,
            ExportingState.SOFT_PAUSED),
        Arguments.of(
            (Function<ExportingStateController.ByTenant, CompletableFuture<Void>>)
                ExportingStateController.ByTenant::resumeExporting,
            ExportingState.EXPORTING));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> emptyPlan() {
    return CompletableFuture.completedFuture(
        Either.right(new ClusterConfigurationChangeResponse(0, Map.of(), Map.of(), List.of())));
  }
}
