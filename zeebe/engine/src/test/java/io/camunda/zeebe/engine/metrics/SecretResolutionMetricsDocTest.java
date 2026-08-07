/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionCallResult;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionKeyNames;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionOutcome;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The doc enum is the documentation for these meters, so it is worth asserting on. Renaming a meter
 * or a tag key silently breaks every dashboard and alert built on it, and there is no repo-wide
 * check for that.
 */
final class SecretResolutionMetricsDocTest {

  @Test
  void shouldBeNamedAndDocumentedConsistently() {
    // given
    final var meters = (ExtendedMeterDocumentation[]) SecretResolutionMetricsDoc.values();

    // when/then
    assertThat(meters)
        .isNotEmpty()
        .allSatisfy(
            meter -> {
              assertThat(meter.getName())
                  .startsWith("camunda.secret.resolution.")
                  .doesNotContain("..")
                  .doesNotContain("-")
                  .doesNotContain("_");
              assertThat(meter.getDescription()).isNotBlank();
              assertThat(meter.getKeyNames()).contains(SecretResolutionKeyNames.STORE);
              // the partition transition registry stamps these on every meter it forwards
              assertThat(meter.getAdditionalKeyNames()).contains(PartitionKeyNames.values());
            });
  }

  @Test
  void shouldDocumentTheDurationAsATimerInSeconds() {
    // given
    final var meter = SecretResolutionMetricsDoc.RESOLUTION_DURATION;

    // when/then
    assertThat(meter.getName()).isEqualTo("camunda.secret.resolution.duration");
    assertThat(meter.getType()).isEqualTo(Type.TIMER);
    assertThat(meter.getBaseUnit()).isEqualTo("seconds");
    // a batch call cannot carry a per-reference outcome, so the timer splits on the call itself —
    // without that, a store timing out lands in the same histogram as the calls that came back
    assertThat(meter.getKeyNames())
        .containsExactly((KeyName) SecretResolutionKeyNames.STORE, SecretResolutionKeyNames.RESULT);
    // a remote store call can take orders of magnitude longer than a local one, so the buckets have
    // to span that range rather than stopping at the default upper bound
    assertThat(meter.getTimerSLOs()).isNotEmpty();
  }

  @Test
  void shouldDocumentACoarserResultDomainForTheTimerThanForTheCounter() {
    // given/when/then — both meters tag `result`, but a batch call has no per-reference outcome
    assertThat(SecretResolutionCallResult.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder("RETURNED", "STORE_UNAVAILABLE", "ERROR");
    assertThat(SecretResolutionOutcome.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder(
            "RESOLVED",
            "NOT_FOUND",
            "ACCESS_DENIED",
            "INVALID_REF",
            "UNREADABLE",
            "STORE_UNAVAILABLE",
            "ERROR");
  }

  @Test
  void shouldGiveTheSameResultValueTheSameMeaningOnBothMeters() {
    // given — the timer's own value for a call that came back, which has no per-reference meaning
    final var timerOnlyValue = SecretResolutionCallResult.RETURNED.name();

    // when
    final var sharedValues =
        Stream.of(SecretResolutionCallResult.values())
            .map(Enum::name)
            .filter(name -> !name.equals(timerOnlyValue))
            .toList();

    // then — a `result` value that appears on both meters must mean the same failure on both,
    // otherwise the same legend on a dashboard reads as two different things
    assertThat(sharedValues)
        .isNotEmpty()
        .allSatisfy(
            name ->
                assertThat(SecretResolutionOutcome.values()).extracting(Enum::name).contains(name));
  }

  @Test
  void shouldGiveTheNoStoreSentinelAValueNoStoreIdCanTake() {
    // given — a store ID is a property-path segment under camunda.secrets.stores.<type>.<id>, so
    // it looks like `main`, `store-a` or `aws-main`
    final var plainPropertySegment = "[A-Za-z0-9._-]+";

    // when/then — a sentinel spelled like one of those would silently merge a configured store's
    // series with the no-store one, and BoundedMeterCache would hand both the same meter
    assertThat(SecretResolutionKeyNames.NO_STORE).doesNotMatch(plainPropertySegment).isNotBlank();
  }

  @Test
  void shouldNotProduceTheNonTerminalOutcomesFromAStoreErrorCode() {
    // given/when/then — STORE_UNAVAILABLE and ERROR are engine-level, not per-secret, so a store
    // error code must never map onto them
    assertThat(SecretErrorCode.values())
        .extracting(SecretResolutionOutcome::from)
        .doesNotContain(SecretResolutionOutcome.STORE_UNAVAILABLE, SecretResolutionOutcome.ERROR);
  }

  @Test
  void shouldDocumentTheOutcomeAsACounterTaggedByStoreAndResult() {
    // given
    final var meter = SecretResolutionMetricsDoc.RESOLUTION_OUTCOME;

    // when/then
    assertThat(meter.getName()).isEqualTo("camunda.secret.resolution.outcome");
    assertThat(meter.getType()).isEqualTo(Type.COUNTER);
    assertThat(meter.getKeyNames())
        .containsExactly(SecretResolutionKeyNames.STORE, SecretResolutionKeyNames.RESULT);
  }

  @Test
  void shouldDocumentHowFarTheOutcomeCounterCanBeTrusted() {
    // given
    final var description = SecretResolutionMetricsDoc.RESOLUTION_OUTCOME.getDescription();

    // when/then — the counter is neither one-per-reference nor exact, and both caveats have to
    // reach whoever builds an alert on it: ERROR counts per cycle, and a terminal outcome can be
    // counted twice when a reference is resolved again before its command has been processed
    assertThat(description).contains("ERROR").contains("over-count");
  }

  @Test
  void shouldMapEveryStoreErrorCodeToItsOwnOutcome() {
    // given/when
    final var outcomes =
        Stream.of(SecretErrorCode.values()).map(SecretResolutionOutcome::from).toList();

    // then — a new error code must not be folded onto a tag value another code already uses, which
    // would make two different failures indistinguishable on a dashboard. Matching names are not
    // asserted: the outcome domain is a copy of SecretErrorCode precisely so that renaming a store
    // API constant does not silently rename a metric tag value.
    assertThat(outcomes).doesNotHaveDuplicates().hasSameSizeAs(SecretErrorCode.values());
  }
}
