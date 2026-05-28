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

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HandlerRegistryTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @Test
  void shouldRouteToHandlerByValueTypeAndIntent() {
    // given
    final var handled = new AtomicBoolean(false);
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                record -> handled.set(true))
            .apply(testContext());

    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED));

    // when
    registry.handle(record);

    // then
    assertThat(handled).isTrue();
  }

  @Test
  void shouldNotRouteWhenIntentDoesNotMatch() {
    // given
    final var handled = new AtomicBoolean(false);
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                record -> handled.set(true))
            .apply(testContext());

    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED));

    // when
    registry.handle(record);

    // then
    assertThat(handled).isFalse();
  }

  @Test
  void shouldSupportMultipleHandlersForSameValueType() {
    // given
    final var activatedHandled = new AtomicBoolean(false);
    final var completedHandled = new AtomicBoolean(false);
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                record -> activatedHandled.set(true))
            .register(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                record -> completedHandled.set(true))
            .apply(testContext());

    final var activatedRecord =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED));
    final var completedRecord =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED));

    // when
    registry.handle(activatedRecord);
    registry.handle(completedRecord);

    // then
    assertThat(activatedHandled).isTrue();
    assertThat(completedHandled).isTrue();
  }

  @Test
  void shouldRejectDuplicateRegistration() {
    // given
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED, record -> {});

    // when / then
    assertThatThrownBy(
            () ->
                registry.register(
                    ValueType.PROCESS_INSTANCE,
                    ProcessInstanceIntent.ELEMENT_ACTIVATED,
                    record -> {}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  @Test
  void shouldDoNothingForUnregisteredValueType() {
    // given
    final var registry = new HandlerRegistry().apply(testContext());

    final var record = FACTORY.generateRecord(ValueType.JOB);

    // when / then — no exception
    registry.handle(record);
  }

  private static ExporterTestContext testContext() {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("test", new AnalyticsExporterConfig()))
        .setPartitionId(1);
  }
}
