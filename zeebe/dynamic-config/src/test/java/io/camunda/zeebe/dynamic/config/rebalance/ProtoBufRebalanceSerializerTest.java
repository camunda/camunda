/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.dynamic.config.rebalance.protocol.Rebalance;
import io.camunda.zeebe.dynamic.config.serializer.DecodingFailed;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ProtoBufRebalanceSerializerTest {

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
        new TriggerRebalanceRequest(new RebalanceOverrides(8192L, Duration.ofMinutes(2), 7), true);

    // when
    final var decoded =
        serializer.decodeTriggerRebalanceRequest(serializer.encodeTriggerRebalanceRequest(request));

    // then
    assertThat(decoded).isEqualTo(request);
  }

  @Test
  void shouldDistinguishAnOverriddenSettingFromAnAbsentOne() {
    // given
    final var request = new TriggerRebalanceRequest(new RebalanceOverrides(0L, null, null), false);

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
                42, new RebalanceOverrides(null, Duration.ofSeconds(15), null), true, true),
            new RebalanceStatus.Completed(41, RebalanceOutcome.CANCELLED));

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(RebalanceOutcome.class)
  void shouldRoundTripEveryOutcome(final RebalanceOutcome outcome) {
    // given
    final var status = new RebalanceStatus(null, new RebalanceStatus.Completed(7, outcome));

    // when
    final var decoded = serializer.decodeRebalanceStatusResponse(serializer.encodeResponse(status));

    // then
    assertThat(decoded.get()).isEqualTo(status);
  }

  @Test
  void shouldRejectAnUnspecifiedOutcome() {
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
