/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.serializer.DecodingFailed;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.rebalance.protocol.Rebalance;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ProtoBufRebalanceSerializerTest {

  private static final Instant STARTED_AT = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant FINISHED_AT = Instant.parse("2024-01-01T00:00:05Z");
  private static final ClusterLeadershipStatus LEADERSHIP_STATUS =
      ClusterLeadershipStatus.aggregateOf(
          List.of(
              new PartitionLeadershipStatus(
                  "default",
                  1,
                  MemberId.from("0"),
                  MemberId.from("1"),
                  PartitionLeadershipStatus.State.TRANSFERRING),
              new PartitionLeadershipStatus(
                  "tenant-a",
                  2,
                  MemberId.from("2"),
                  MemberId.from("2"),
                  PartitionLeadershipStatus.State.BALANCED)));

  private final ProtoBufRebalanceSerializer serializer = new ProtoBufRebalanceSerializer();

  @Test
  void shouldRoundTripATriggerThatOverridesNothing() {
    // given
    final var request = TriggerRebalanceRequest.withConfiguredSettings();

    // when
    final var decoded =
        serializer.decodeTriggerRebalanceRequest(serializer.encodeTriggerRebalanceRequest(request));

    // then
    assertThat(decoded).isEqualTo(request);
    assertThat(decoded.overrides()).isEqualTo(RebalanceOverrides.none());
  }

  @Test
  void shouldRoundTripATriggerThatOverridesEverything() {
    // given
    final var request =
        new TriggerRebalanceRequest(
            new RebalanceOverrides(8192L, Duration.ofMinutes(2), 7, Duration.ZERO), true);

    // when
    final var decoded =
        serializer.decodeTriggerRebalanceRequest(serializer.encodeTriggerRebalanceRequest(request));

    // then
    assertThat(decoded).isEqualTo(request);
  }

  @Test
  void shouldDistinguishAnOverriddenSettingFromAnAbsentOne() {
    // given
    final var request =
        new TriggerRebalanceRequest(new RebalanceOverrides(0L, null, null, null), false);

    // when
    final var decoded =
        serializer.decodeTriggerRebalanceRequest(serializer.encodeTriggerRebalanceRequest(request));

    // then
    assertThat(decoded.overrides().replicationLagThreshold()).isZero();
    assertThat(decoded.overrides().replicationTimeout()).isNull();
    assertThat(decoded.overrides().maxTransferAttempts()).isNull();
  }

  @Test
  void shouldRoundTripAnIdleStatus() {
    // given
    final var status = RebalanceStatus.idle();

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @Test
  void shouldRoundTripAStatusWithARunningAndACompletedRebalance() {
    // given
    final var status =
        new RebalanceStatus(
            new RebalanceStatus.Running(
                42,
                new RebalanceOverrides(null, Duration.ofSeconds(15), null, null),
                true,
                true,
                List.of(
                    new PartitionRebalance(
                        "default",
                        1,
                        MemberId.from("0"),
                        MemberId.from("1"),
                        PartitionRebalanceProgress.TRANSFERRING)),
                STARTED_AT),
            new RebalanceStatus.Completed(
                41,
                RebalanceOutcome.CANCELLED,
                List.of(PartitionRebalance.alreadyLeader("tenant-a", 2, MemberId.from("2"))),
                STARTED_AT,
                FINISHED_AT),
            LEADERSHIP_STATUS);

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(RebalanceOutcome.class)
  void shouldRoundTripEveryOutcome(final RebalanceOutcome outcome) {
    // given
    final var status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(7, outcome, List.of(), STARTED_AT, FINISHED_AT),
            LEADERSHIP_STATUS);

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(PartitionRebalanceProgress.class)
  void shouldRoundTripEveryPartitionProgress(final PartitionRebalanceProgress progress) {
    // given
    final var partition =
        progress == PartitionRebalanceProgress.COMPLETED
            ? PartitionRebalance.alreadyLeader("default", 3, MemberId.from("1"))
            : new PartitionRebalance("default", 3, null, MemberId.from("1"), progress);
    final var status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(
                7, RebalanceOutcome.COMPLETED, List.of(partition), STARTED_AT, FINISHED_AT),
            LEADERSHIP_STATUS);

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(PartitionRebalanceOutcome.class)
  void shouldRoundTripEveryPartitionOutcome(final PartitionRebalanceOutcome outcome) {
    // given
    final var partition =
        new PartitionRebalance(
            "default",
            3,
            MemberId.from("1"),
            MemberId.from("1"),
            PartitionRebalanceProgress.COMPLETED,
            outcome);
    final var status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(
                7, RebalanceOutcome.COMPLETED, List.of(partition), STARTED_AT, FINISHED_AT),
            LEADERSHIP_STATUS);

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @Test
  void shouldRoundTripAPartitionWithNoCurrentLeader() {
    // given
    final var partition =
        new PartitionRebalance(
            "default", 4, null, MemberId.from("1"), PartitionRebalanceProgress.PENDING);
    final var status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(
                7, RebalanceOutcome.COMPLETED, List.of(partition), STARTED_AT, FINISHED_AT),
            LEADERSHIP_STATUS);

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get().lastCompleted().partitions()).containsExactly(partition);
  }

  @Test
  void shouldRejectAPartitionWithNoDesiredLeader() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(
                        Rebalance.CompletedRebalance.newBuilder()
                            .setRebalanceId(7)
                            .setOutcome(Rebalance.CompletedRebalance.RebalanceOutcome.COMPLETED)
                            .addPartitions(
                                Rebalance.PartitionRebalance.newBuilder()
                                    .setPhysicalTenantId("default")
                                    .setPartitionId(4)
                                    .setProgress(Rebalance.PartitionRebalance.Progress.PENDING))))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(DecodingFailed.class);
  }

  @Test
  void shouldRejectAnUnspecifiedRebalanceOutcome() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(Rebalance.CompletedRebalance.newBuilder().setRebalanceId(7)))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(DecodingFailed.class);
  }

  @Test
  void shouldRejectAnUnspecifiedPartitionProgress() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(
                        Rebalance.CompletedRebalance.newBuilder()
                            .setRebalanceId(7)
                            .setOutcome(Rebalance.CompletedRebalance.RebalanceOutcome.COMPLETED)
                            .addPartitions(
                                Rebalance.PartitionRebalance.newBuilder()
                                    .setPhysicalTenantId("default")
                                    .setPartitionId(3))))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(DecodingFailed.class);
  }

  @Test
  void shouldRejectAnUnrecognizedPartitionProgress() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(
                        Rebalance.CompletedRebalance.newBuilder()
                            .setRebalanceId(7)
                            .setOutcome(Rebalance.CompletedRebalance.RebalanceOutcome.COMPLETED)
                            .addPartitions(
                                Rebalance.PartitionRebalance.newBuilder()
                                    .setPhysicalTenantId("default")
                                    .setPartitionId(3)
                                    .setProgressValue(999))))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(DecodingFailed.class);
  }

  @Test
  void shouldRejectACompletedPartitionWithNoOutcome() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(
                        Rebalance.CompletedRebalance.newBuilder()
                            .setRebalanceId(7)
                            .setOutcome(Rebalance.CompletedRebalance.RebalanceOutcome.COMPLETED)
                            .addPartitions(
                                Rebalance.PartitionRebalance.newBuilder()
                                    .setPhysicalTenantId("default")
                                    .setPartitionId(3)
                                    .setDesiredLeader("1")
                                    .setProgress(Rebalance.PartitionRebalance.Progress.COMPLETED))))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectACompletedPartitionWithAnUnspecifiedOutcome() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(
                        Rebalance.CompletedRebalance.newBuilder()
                            .setRebalanceId(7)
                            .setOutcome(Rebalance.CompletedRebalance.RebalanceOutcome.COMPLETED)
                            .addPartitions(
                                Rebalance.PartitionRebalance.newBuilder()
                                    .setPhysicalTenantId("default")
                                    .setPartitionId(3)
                                    .setProgress(Rebalance.PartitionRebalance.Progress.COMPLETED)
                                    .setOutcome(
                                        Rebalance.PartitionRebalance.Outcome.OUTCOME_UNSPECIFIED))))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(DecodingFailed.class);
  }

  @Test
  void shouldRejectACompletedPartitionWithAnUnrecognizedOutcome() {
    // given
    final var encoded =
        Rebalance.Response.newBuilder()
            .setStatus(
                Rebalance.RebalanceStatusResponse.newBuilder()
                    .setLastCompleted(
                        Rebalance.CompletedRebalance.newBuilder()
                            .setRebalanceId(7)
                            .setOutcome(Rebalance.CompletedRebalance.RebalanceOutcome.COMPLETED)
                            .addPartitions(
                                Rebalance.PartitionRebalance.newBuilder()
                                    .setPhysicalTenantId("default")
                                    .setPartitionId(3)
                                    .setProgress(Rebalance.PartitionRebalance.Progress.COMPLETED)
                                    .setOutcomeValue(999))))
            .build()
            .toByteArray();

    // when/then
    assertThatThrownBy(() -> serializer.decodeRebalanceStatusResponse(encoded))
        .isInstanceOf(DecodingFailed.class);
  }

  @Test
  void shouldRoundTripACancellation() {
    // given
    final var response = new CancelRebalanceResponse(true);

    // when
    final var decoded =
        serializer.decodeCancelRebalanceResponse(serializer.encodeResponse(response));

    // then
    assertThat(decoded.get()).isEqualTo(response);
  }

  @ParameterizedTest
  @EnumSource(RebalanceErrorCode.class)
  void shouldRoundTripAnErrorOnEitherResponse(final RebalanceErrorCode code) {
    // given
    final var error = new RebalanceErrorResponse(code, "refused");

    // when
    final var encoded = serializer.encodeResponse(error);

    // then
    assertThat(serializer.decodeRebalanceStatusResponse(encoded).getLeft()).isEqualTo(error);
    assertThat(serializer.decodeCancelRebalanceResponse(encoded).getLeft()).isEqualTo(error);
  }
}
