/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import org.junit.jupiter.api.Test;

class AnalyticsExporterDigestTest {

  @Test
  void shouldProduceSameHashForIdenticalInput() {
    // given
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha());
    final var config = new AnalyticsExporterConfig().setSamplingRate(1.0);

    // when
    final var first = AnalyticsExporterDigest.compute(registry, config);
    final var second = AnalyticsExporterDigest.compute(registry, config);

    // then
    assertThat(first).isEqualTo(second).hasSize(64);
  }

  @Test
  void shouldProduceSameHashRegardlessOfRegistrationOrder() {
    // given — same handlers registered in different orders
    final var config = new AnalyticsExporterConfig();
    final var registryA =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha())
            .register(ValueType.USER_TASK, UserTaskIntent.CREATED, new StubHandlerBeta());
    final var registryB =
        new HandlerRegistry()
            .register(ValueType.USER_TASK, UserTaskIntent.CREATED, new StubHandlerBeta())
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha());

    // when / then
    assertThat(AnalyticsExporterDigest.compute(registryA, config))
        .isEqualTo(AnalyticsExporterDigest.compute(registryB, config));
  }

  @Test
  void shouldProduceDifferentHashWhenHandlerIsAdded() {
    // given
    final var config = new AnalyticsExporterConfig();
    final var registryA =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha());
    final var registryB =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha())
            .register(ValueType.USER_TASK, UserTaskIntent.CREATED, new StubHandlerBeta());

    // when / then
    assertThat(AnalyticsExporterDigest.compute(registryA, config))
        .isNotEqualTo(AnalyticsExporterDigest.compute(registryB, config));
  }

  @Test
  void shouldProduceDifferentHashWhenHandlerImplementationChanges() {
    // given — two handlers registered at the same key but with different bytecodes
    final var config = new AnalyticsExporterConfig();
    final var registryA =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha());
    final var registryB =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerBeta());

    // when / then
    assertThat(AnalyticsExporterDigest.compute(registryA, config))
        .isNotEqualTo(AnalyticsExporterDigest.compute(registryB, config));
  }

  @Test
  void shouldProduceDifferentHashWhenSamplingRateChanges() {
    // given
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha());
    final var configA = new AnalyticsExporterConfig().setSamplingRate(1.0);
    final var configB = new AnalyticsExporterConfig().setSamplingRate(0.5);

    // when / then
    assertThat(AnalyticsExporterDigest.compute(registry, configA))
        .isNotEqualTo(AnalyticsExporterDigest.compute(registry, configB));
  }

  @Test
  void shouldRejectLambdaHandler() {
    // given — lambda handlers are synthetic and have no stable .class resource
    final var config = new AnalyticsExporterConfig();
    final AnalyticsHandler<RecordValue> lambdaHandler = record -> {};
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                lambdaHandler);

    // when / then
    assertThatThrownBy(() -> AnalyticsExporterDigest.compute(registry, config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lambdas");
  }

  // Two concrete handler classes with different bytecodes to test implementation-change detection.
  // They differ in their handle() body so the compiler produces distinct .class files.
  private static final class StubHandlerAlpha implements AnalyticsHandler<RecordValue> {
    @Override
    public void handle(final Record<RecordValue> record) {}
  }

  private static final class StubHandlerBeta implements AnalyticsHandler<RecordValue> {
    @Override
    public void handle(final Record<RecordValue> record) {
      // intentionally different body so javac produces different bytecode
      final var unused = record.getPosition();
    }
  }
}
