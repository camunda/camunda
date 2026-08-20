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
import java.util.Set;
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
  void shouldRejectAnonymousHandler() {
    // given — anonymous classes have no stable .class resource on the classpath
    final var config = new AnalyticsExporterConfig();
    final AnalyticsHandler<RecordValue> anonHandler =
        new AnalyticsHandler<>() {
          @Override
          public AnalyticsCategory category() {
            return AnalyticsCategory.CONTRACTUAL;
          }

          @Override
          public void handle(final Record<RecordValue> record) {}
        };
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                anonHandler);

    // when / then
    assertThatThrownBy(() -> AnalyticsExporterDigest.compute(registry, config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lambdas");
  }

  @Test
  void shouldProduceDifferentHashWhenActiveCategoriesChange() {
    // given — same handlers registered but different active categories yield different registries
    final var config = new AnalyticsExporterConfig();
    final var registryAll =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha())
            .register(ValueType.USER_TASK, UserTaskIntent.CREATED, new StubHandlerBeta());
    final var registryContractualOnly =
        new HandlerRegistry(Set.of(AnalyticsCategory.CONTRACTUAL))
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                new StubHandlerAlpha())
            .register(ValueType.USER_TASK, UserTaskIntent.CREATED, new StubHandlerBeta());

    // when / then — Beta is OPTIONAL so it's excluded from the second registry
    assertThat(AnalyticsExporterDigest.compute(registryAll, config))
        .isNotEqualTo(AnalyticsExporterDigest.compute(registryContractualOnly, config));
  }

  // Two concrete handler classes with different bytecodes to test implementation-change detection.
  // They differ in their handle() body so the compiler produces distinct .class files.
  private static final class StubHandlerAlpha implements AnalyticsHandler<RecordValue> {
    @Override
    public AnalyticsCategory category() {
      return AnalyticsCategory.CONTRACTUAL;
    }

    @Override
    public void handle(final Record<RecordValue> record) {}
  }

  private static final class StubHandlerBeta implements AnalyticsHandler<RecordValue> {
    @Override
    public AnalyticsCategory category() {
      return AnalyticsCategory.OPTIONAL;
    }

    @Override
    public void handle(final Record<RecordValue> record) {
      // intentionally different body so javac produces different bytecode
      final var unused = record.getPosition();
    }
  }
}
