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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

  private static final String OTHER_TENANT = "tenant-b";

  private final ClusterConfigurationManagementRequestSender requestSender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final DynamicConfigExportingStateController exportingStateController =
      new DynamicConfigExportingStateController(
          requestSender, Duration.ofMillis(1), Duration.ofSeconds(10));
  private final ExportingStateController.ByTenant controller =
      exportingStateController.getByTenant(DEFAULT_PHYSICAL_TENANT_ID);

  @ParameterizedTest
  @MethodSource("operations")
  void shouldSubmitExpectedTargetStateForTheResolvedTenant(
      final Function<ExportingStateController.ByTenant, CompletableFuture<Void>> operation,
      final ExportingState expectedState) {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    operation.apply(controller).join();

    // then
    assertThat(captor.getValue().state()).isEqualTo(expectedState);
    assertThat(captor.getValue().physicalTenantId()).contains(DEFAULT_PHYSICAL_TENANT_ID);
  }

  @ParameterizedTest
  @MethodSource("operations")
  void shouldSubmitTheTenantItWasResolvedFor(
      final Function<ExportingStateController.ByTenant, CompletableFuture<Void>> operation,
      final ExportingState ignoredExpectedState) {
    // given — a controller resolved for a non-default tenant
    final var otherController = exportingStateController.getByTenant(OTHER_TENANT);
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    operation.apply(otherController).join();

    // then
    assertThat(captor.getValue().physicalTenantId()).contains(OTHER_TENANT);
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

    // when
    final var thrown = catchThrowable(() -> operation.apply(controller).join());

    // then
    assertThat(thrown).hasMessageContaining("nope");
  }

  @ParameterizedTest
  @MethodSource("operations")
  void shouldPollUntilTheSubmittedChangeCompletes(
      final Function<ExportingStateController.ByTenant, CompletableFuture<Void>> operation,
      final ExportingState ignoredExpectedState) {
    // given — the submission plans a real change, so the controller must poll for it to resolve
    // rather than short-circuiting on an empty plan
    when(requestSender.changeExportingState(any())).thenReturn(pendingPlan(7));
    when(requestSender.getTopology())
        .thenReturn(topologyWithChange(pending(7)))
        .thenReturn(topologyWithChange(completed(7, PhasedChangePlanStatus.COMPLETED)));

    // when
    final var result = operation.apply(controller);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    verify(requestSender, atLeast(2)).getTopology();
  }

  @Test
  void shouldAggregateStatusWhenReplicasAgree() {
    // given
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(MemberId.from("0"), ExportingState.SOFT_PAUSED))));

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
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(
                        MemberId.from("0"), ExportingState.PAUSED,
                        MemberId.from("1"), ExportingState.EXPORTING))));

    // when
    final var status = controller.getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldReportExportingStatusWhenNeverControlledByConfig() {
    // given - a replica config never touched by a state-change operation is UNKNOWN
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(MemberId.from("0"), ExportingState.UNKNOWN))));

    // when
    final var status = controller.getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.EXPORTING);
  }

  @Test
  void shouldAggregateStatusOnlyForTheResolvedTenant() {
    // given — the default tenant is paused, tenant-b is exporting; the controller was resolved
    // for tenant-b only
    final var tenantBController = exportingStateController.getByTenant(OTHER_TENANT);
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(MemberId.from("0"), ExportingState.PAUSED),
                    OTHER_TENANT,
                    Map.of(MemberId.from("1"), ExportingState.EXPORTING))));

    // when
    final var status = tenantBController.getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.EXPORTING);
  }

  @Test
  void shouldFailStatusQueryWhenTheResolvedTenantIsAbsentFromTheTopology() {
    // given — the topology has no partition group for tenant-b yet
    final var tenantBController = exportingStateController.getByTenant(OTHER_TENANT);
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(MemberId.from("0"), ExportingState.EXPORTING))));

    // when
    final var thrown = catchThrowable(() -> tenantBController.getExportingStatus().join());

    // then — same rejection as changeState(), rather than folding "no such tenant" into MIXED
    assertThat(thrown)
        .hasCauseInstanceOf(ClusterConfigurationRequestFailedException.NotFound.class);
  }

  @Test
  void shouldFailStatusQueryForADisabledTenant() {
    // given — tenant-b is disabled (still present, retained but frozen) and reports a
    // seemingly-converged state that changeState() would reject as not found
    final var tenantBController = exportingStateController.getByTenant(OTHER_TENANT);
    final var disabledGroup = group(Map.of(MemberId.from("0"), ExportingState.PAUSED)).disable();
    when(requestSender.getTopology())
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new CurrentClusterConfiguration(
                        CurrentClusterConfiguration.INITIAL_VERSION,
                        GlobalConfiguration.init(),
                        Map.of(OTHER_TENANT, disabledGroup),
                        PhasedChangeState.empty()))));

    // when
    final var thrown = catchThrowable(() -> tenantBController.getExportingStatus().join());

    // then — matches changeState()'s rejection of the same tenant, instead of reporting a frozen
    // status that would look indistinguishable from a real, converged one
    assertThat(thrown)
        .hasCauseInstanceOf(ClusterConfigurationRequestFailedException.NotFound.class);
  }

  @ParameterizedTest
  @MethodSource("clusterWideOperations")
  void shouldSubmitClusterWideOperationsWithNoPhysicalTenant(
      final Function<ExportingStateController.ClusterWide, CompletableFuture<Void>> operation,
      final ExportingState expectedState) {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    operation.apply(exportingStateController.clusterWide()).join();

    // then — one atomic request, unscoped, rather than one request per tenant
    assertThat(captor.getValue().state()).isEqualTo(expectedState);
    assertThat(captor.getValue().physicalTenantId()).isEmpty();
  }

  @Test
  void shouldAggregateClusterWideStatusAcrossEveryActiveTenant() {
    // given
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(MemberId.from("0"), ExportingState.PAUSED),
                    OTHER_TENANT,
                    Map.of(MemberId.from("1"), ExportingState.PAUSED))));

    // when
    final var status = exportingStateController.clusterWide().getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.PAUSED);
  }

  @Test
  void shouldReportMixedClusterWideStatusWhenTenantsDisagree() {
    // given
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    DEFAULT_PHYSICAL_TENANT_ID,
                    Map.of(MemberId.from("0"), ExportingState.PAUSED),
                    OTHER_TENANT,
                    Map.of(MemberId.from("1"), ExportingState.EXPORTING))));

    // when
    final var status = exportingStateController.clusterWide().getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  void shouldExcludeADisabledTenantFromTheClusterWideStatus() {
    // given — tenant-b is disabled and paused; only the default tenant's exporting state should
    // count towards the cluster-wide aggregate
    final var disabledGroup = group(Map.of(MemberId.from("1"), ExportingState.PAUSED)).disable();
    when(requestSender.getTopology())
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new CurrentClusterConfiguration(
                        CurrentClusterConfiguration.INITIAL_VERSION,
                        GlobalConfiguration.init(),
                        Map.of(
                            DEFAULT_PHYSICAL_TENANT_ID,
                            group(Map.of(MemberId.from("0"), ExportingState.EXPORTING)),
                            OTHER_TENANT,
                            disabledGroup),
                        PhasedChangeState.empty()))));

    // when
    final var status = exportingStateController.clusterWide().getExportingStatus();

    // then
    assertThat(status).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(ExportingStatus.EXPORTING);
  }

  private static Stream<Arguments> clusterWideOperations() {
    return Stream.of(
        Arguments.of(
            (Function<ExportingStateController.ClusterWide, CompletableFuture<Void>>)
                ExportingStateController.ClusterWide::pauseExporting,
            ExportingState.PAUSED),
        Arguments.of(
            (Function<ExportingStateController.ClusterWide, CompletableFuture<Void>>)
                ExportingStateController.ClusterWide::softPauseExporting,
            ExportingState.SOFT_PAUSED),
        Arguments.of(
            (Function<ExportingStateController.ClusterWide, CompletableFuture<Void>>)
                ExportingStateController.ClusterWide::resumeExporting,
            ExportingState.EXPORTING));
  }

  private static CompletableFuture<Either<ErrorResponse, CurrentClusterConfiguration>> topology(
      final Map<String, Map<MemberId, ExportingState>> statesByTenantAndMember) {
    final Map<String, PartitionGroupConfiguration> partitionGroups =
        statesByTenantAndMember.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> group(entry.getValue())));
    return CompletableFuture.completedFuture(
        Either.right(
            new CurrentClusterConfiguration(
                CurrentClusterConfiguration.INITIAL_VERSION,
                GlobalConfiguration.init(),
                partitionGroups,
                PhasedChangeState.empty())));
  }

  private static PartitionGroupConfiguration group(
      final Map<MemberId, ExportingState> statesByMember) {
    var group = PartitionGroupConfiguration.empty(PartitionGroupConfiguration.INITIAL_VERSION);
    for (final var entry : statesByMember.entrySet()) {
      group =
          group.addMember(
              entry.getKey(),
              BrokerPartitionState.initialize(
                  Map.of(
                      1,
                      PartitionState.active(
                          1,
                          DynamicPartitionConfig.init()
                              .updateExporting(config -> config.withState(entry.getValue()))))));
    }
    return group;
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
        Either.right(
            new ClusterConfigurationChangeResponse(
                0,
                new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                    Map.of(), Map.of(), List.of()),
                new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                    CurrentClusterConfiguration.init(),
                    CurrentClusterConfiguration.init(),
                    List.of()))));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> pendingPlan(
      final long changeId) {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfigurationChangeResponse(
                changeId,
                new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                    Map.of(), Map.of(), List.of()),
                new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                    CurrentClusterConfiguration.init(),
                    CurrentClusterConfiguration.init(),
                    List.of(new GlobalPhase(List.of()))))));
  }

  private CompletableFuture<Either<ErrorResponse, CurrentClusterConfiguration>> topologyWithChange(
      final PhasedChangePlan pending) {
    return CompletableFuture.completedFuture(
        Either.right(
            new CurrentClusterConfiguration(
                CurrentClusterConfiguration.INITIAL_VERSION,
                GlobalConfiguration.init(),
                Map.of(),
                new PhasedChangeState(
                    pending.id() + 1, Map.of(pending.id(), pending), List.of()))));
  }

  private CompletableFuture<Either<ErrorResponse, CurrentClusterConfiguration>> topologyWithChange(
      final CompletedPhasedChange completed) {
    return CompletableFuture.completedFuture(
        Either.right(
            new CurrentClusterConfiguration(
                CurrentClusterConfiguration.INITIAL_VERSION,
                GlobalConfiguration.init(),
                Map.of(),
                new PhasedChangeState(completed.id() + 1, Map.of(), List.of(completed)))));
  }

  private PhasedChangePlan pending(final long changeId) {
    return PhasedChangePlan.init(changeId, List.of(new GlobalPhase(List.of())), Instant.now());
  }

  private CompletedPhasedChange completed(
      final long changeId, final PhasedChangePlanStatus status) {
    return new CompletedPhasedChange(changeId, status, Instant.now(), Instant.now());
  }
}
