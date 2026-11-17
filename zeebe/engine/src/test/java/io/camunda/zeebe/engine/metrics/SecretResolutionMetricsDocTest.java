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
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionCycleDelayReason;
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
              // CYCLE_DELAY is the one exception: it measures the scheduler's own per-cycle
              // decision, not an operation against a particular store, so it has no store
              // dimension to tag (see shouldDocumentTheCycleDelayAsATimerTaggedByResultOnly)
              if (meter != SecretResolutionMetricsDoc.CYCLE_DELAY) {
                assertThat(meter.getKeyNames()).contains(SecretResolutionKeyNames.STORE);
              }
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
            "STORE_UNAVAILABLE");
  }

  @Test
  void shouldCountOnlySecretReferencesOnTheOutcomeCounter() {
    // given — the two values that describe a whole call rather than a single reference
    final var callLevelValues =
        Stream.of(SecretResolutionCallResult.RETURNED, SecretResolutionCallResult.ERROR)
            .map(Enum::name)
            .toList();

    // when/then — neither may become an outcome value. A counter whose values carry two units
    // cannot be summed or divided across its `result` tag, so `sum by(store)(rate(..._total[5m]))`
    // and every failure ratio would silently add references and calls together. A quantity measured
    // per call or per cycle belongs on its own meter, as RESOLUTION_CYCLE_ERROR is
    assertThat(SecretResolutionOutcome.values())
        .extracting(Enum::name)
        .doesNotContainAnyElementsOf(callLevelValues);
  }

  @Test
  void shouldGiveTheSameResultValueTheSameMeaningOnBothMeters() {
    // given — the timer's own values: RETURNED because a call that came back has no single
    // per-reference outcome, ERROR because a call that threw resolved no reference at all
    final var timerOnlyValues =
        Stream.of(SecretResolutionCallResult.RETURNED, SecretResolutionCallResult.ERROR)
            .map(Enum::name)
            .toList();

    // when
    final var sharedValues =
        Stream.of(SecretResolutionCallResult.values())
            .map(Enum::name)
            .filter(name -> !timerOnlyValues.contains(name))
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
  void shouldNotProduceTheStoreLevelOutcomeFromAStoreErrorCode() {
    // given/when/then — STORE_UNAVAILABLE says the store served no reference at all, so a code the
    // store returned for one specific reference must never map onto it
    assertThat(SecretErrorCode.values())
        .extracting(SecretResolutionOutcome::from)
        .doesNotContain(SecretResolutionOutcome.STORE_UNAVAILABLE);
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

    // when/then — the counter is per reference but not exact, and both caveats have to reach
    // whoever builds an alert on it: a reference with retry attempts left is not counted at all,
    // and one that is resolved again before its command has been processed is counted twice
    assertThat(description).contains("retry attempts left").contains("over-count");
  }

  @Test
  void shouldDocumentTheCycleErrorAsACounterTaggedByStoreOnly() {
    // given
    final var meter = SecretResolutionMetricsDoc.RESOLUTION_CYCLE_ERROR;

    // when/then — no `result` tag: the meter has a single meaning, so there is no value domain to
    // aggregate across and no way to add a cycle count to a reference count by accident
    assertThat(meter.getName()).isEqualTo("camunda.secret.resolution.cycle.error");
    assertThat(meter.getType()).isEqualTo(Type.COUNTER);
    assertThat(meter.getKeyNames()).containsExactly(SecretResolutionKeyNames.STORE);
  }

  @Test
  void shouldDocumentThatTheCycleErrorCountsCyclesRatherThanReferences() {
    // given
    final var description = SecretResolutionMetricsDoc.RESOLUTION_CYCLE_ERROR.getDescription();

    // when/then — the unit is the whole point of the separate meter, and reading it as a reference
    // count would make it look like a backlog rather than a bug rate
    assertThat(description).contains("cycles rather than references");
  }

  @Test
  void shouldDocumentTheCycleDelayAsATimerTaggedByResultOnly() {
    // given
    final var meter = SecretResolutionMetricsDoc.CYCLE_DELAY;

    // when/then: no `store` tag, since the delay is the scheduler's own per-cycle decision,
    // covering every store a cycle looked at (or none), not an operation against one particular
    // store
    assertThat(meter.getName()).isEqualTo("camunda.secret.resolution.cycle.delay");
    assertThat(meter.getType()).isEqualTo(Type.TIMER);
    assertThat(meter.getBaseUnit()).isEqualTo("seconds");
    assertThat(meter.getKeyNames()).containsExactly(SecretResolutionKeyNames.RESULT);
    assertThat(meter.getTimerSLOs()).isNotEmpty();
  }

  @Test
  void shouldDocumentEveryCycleDelayReason() {
    // given/when/then: one value per branch SecretResolutionScheduler's delay choice can take
    assertThat(SecretResolutionCycleDelayReason.values())
        .extracting(Enum::name)
        .containsExactlyInAnyOrder("DRAINING", "WAKE", "IDLE_BACKOFF", "RETRY_COOLDOWN");
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
