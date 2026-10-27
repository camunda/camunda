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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
                contractualHandler(record -> handled.set(true)))
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
                contractualHandler(record -> handled.set(true)))
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
                contractualHandler(record -> activatedHandled.set(true)))
            .register(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ELEMENT_COMPLETED,
                contractualHandler(record -> completedHandled.set(true)))
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
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                contractualHandler(record -> {}));

    // when / then
    assertThatThrownBy(
            () ->
                registry.register(
                    ValueType.PROCESS_INSTANCE,
                    ProcessInstanceIntent.ELEMENT_ACTIVATED,
                    contractualHandler(record -> {})))
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

  @Test
  void shouldExposeRegisteredHandlers() {
    // given
    final var registry =
        new HandlerRegistry()
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                contractualHandler(record -> {}))
            .register(
                ValueType.USER_TASK, UserTaskIntent.CREATED, contractualHandler(record -> {}));

    // when
    final var registered = registry.registeredHandlers();

    // then
    assertThat(registered).containsKey(ValueType.PROCESS_INSTANCE_CREATION);
    assertThat(registered).containsKey(ValueType.USER_TASK);
    assertThat(registered.get(ValueType.PROCESS_INSTANCE_CREATION))
        .containsKey(ProcessInstanceCreationIntent.CREATED);
    assertThat(registered.get(ValueType.USER_TASK)).containsKey(UserTaskIntent.CREATED);
  }

  @Test
  void shouldExcludeHandlersOutsideActiveCategory() {
    // given — registry only accepts CONTRACTUAL; an OPTIONAL handler is registered but excluded
    final var contractualHandled = new AtomicBoolean(false);
    final var optionalHandled = new AtomicBoolean(false);
    final var registry =
        new HandlerRegistry(Set.of(AnalyticsCategory.CONTRACTUAL))
            .register(
                ValueType.PROCESS_INSTANCE_CREATION,
                ProcessInstanceCreationIntent.CREATED,
                contractualHandler(record -> contractualHandled.set(true)))
            .register(
                ValueType.PROCESS_INSTANCE,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                optionalHandler(record -> optionalHandled.set(true)))
            .apply(testContext());

    // when
    registry.handle(
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED)));
    registry.handle(
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)));

    // then
    assertThat(contractualHandled).isTrue();
    assertThat(optionalHandled).isFalse();
    assertThat(registry.registrations())
        .containsExactly(
            java.util.Map.entry(
                ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATED));
  }

  private static ExporterTestContext testContext() {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("test", new AnalyticsExporterConfig()))
        .setPartitionId(1);
  }

  private static AnalyticsHandler<RecordValue> contractualHandler(
      final Consumer<Record<RecordValue>> action) {
    return new AnalyticsHandler<>() {
      @Override
      public AnalyticsCategory category() {
        return AnalyticsCategory.CONTRACTUAL;
      }

      @Override
      public void handle(final Record<RecordValue> record) {
        action.accept(record);
      }
    };
  }

  private static AnalyticsHandler<RecordValue> optionalHandler(
      final Consumer<Record<RecordValue>> action) {
    return new AnalyticsHandler<>() {
      @Override
      public AnalyticsCategory category() {
        return AnalyticsCategory.OPTIONAL;
      }

      @Override
      public void handle(final Record<RecordValue> record) {
        action.accept(record);
      }
    };
  }
}
