/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The doc enum is the documentation for these meters, so it is worth asserting on. Renaming a meter
 * or its tag silently breaks every dashboard and alert built on it, and the description carries the
 * alert operators are told to copy.
 */
final class JobBacklogMetricsDocTest {

  @Test
  void shouldPinTheActivatableJobsMeterOperatorsQueryBy() {
    // given
    final var doc = JobBacklogMetricsDoc.ACTIVATABLE_JOBS;

    // when
    final var name = doc.getName();
    final var type = doc.getType();
    final var keyNames = keyNames(doc);

    // then
    assertThat(name).isEqualTo("zeebe.job.activatable.count");
    assertThat(type).isEqualTo(Type.GAUGE);
    assertThat(keyNames).containsExactly("type");
  }

  @Test
  void shouldPinTheActivationLagMeterOperatorsAlertOn() {
    // given
    final var doc = JobBacklogMetricsDoc.ACTIVATION_LAG;

    // when
    final var name = doc.getName();
    final var type = doc.getType();
    final var baseUnit = doc.getBaseUnit();
    final var keyNames = keyNames(doc);

    // then
    assertThat(name).isEqualTo("zeebe.job.activation.lag");
    assertThat(type).isEqualTo(Type.GAUGE);
    assertThat(baseUnit).isEqualTo("seconds");
    assertThat(keyNames).containsExactly("type");
  }

  @Test
  void shouldDocumentTheForClauseAndTheRestartCaveat() {
    // given
    final var doc = JobBacklogMetricsDoc.ACTIVATION_LAG;

    // when
    final var description = doc.getDescription();

    // then - lag restarts after a restart or leader change, so the alert needs a for: clause
    assertThat(description).contains("for:").containsIgnoringCase("restart");
  }

  @Test
  void shouldDocumentTheAlertAndHowToAggregateAcrossReplicas() {
    // given
    final var doc = JobBacklogMetricsDoc.ACTIVATION_LAG;

    // when
    final var description = doc.getDescription();

    // then - every replica reports the gauge, so the alert must pick the maximum per
    // partition and type or it counts followers' view of the same backlog
    assertThat(description)
        .contains("max by (partition, type)")
        .contains("zeebe_job_activation_lag")
        .containsIgnoringCase("alert");
  }

  @Test
  void shouldGiveEveryMeterItsOwnName() {
    // given
    final var docs = JobBacklogMetricsDoc.values();

    // when
    final var names = Arrays.stream(docs).map(JobBacklogMetricsDoc::getName).toList();

    // then
    assertThat(names).hasSize(2).doesNotHaveDuplicates();
  }

  private static String[] keyNames(final JobBacklogMetricsDoc doc) {
    return Arrays.stream(doc.getKeyNames()).map(KeyName::asString).toArray(String[]::new);
  }
}
