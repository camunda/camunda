/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.engine.processing.storageordinals.StorageOrdinalAware;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class TypedRecordProcessorsTest {

  @Test
  void shouldRejectStorageOrdinalProcessorThatIsNotAware() {
    // given
    final var processors = TypedRecordProcessors.processors();
    final var processor = plainJobProcessor();

    // when / then
    assertThatThrownBy(() -> processors.onCommand(ValueType.JOB, JobIntent.COMPLETE, processor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("StorageOrdinalAware")
        .hasMessageContaining("JOB");
  }

  @Test
  void shouldAcceptStorageOrdinalProcessorThatIsAware() {
    // given
    final var processors = TypedRecordProcessors.processors();
    final var processor = awareJobProcessor();

    // when
    processors.onCommand(ValueType.JOB, JobIntent.COMPLETE, processor);

    // then
    assertThat(
            processors
                .getRecordProcessorMap()
                .get(RecordType.COMMAND, ValueType.JOB, JobIntent.COMPLETE.value()))
        .isSameAs(processor);
  }

  @Test
  void shouldAcceptPlainProcessorForValueTypeWithoutOrdinal() {
    // given
    final var processors = TypedRecordProcessors.processors();
    final var processor = plainDeploymentProcessor();

    // when / then
    assertThatCode(
            () -> processors.onCommand(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, processor))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("ordinalCommands")
  void shouldGuardValueTypeWhoseRecordCarriesAnOrdinal(
      final ValueType valueType, final Intent intent) {
    // given
    final var processors = TypedRecordProcessors.processors();
    final var processor = plainProcessor();

    // when / then
    assertThatThrownBy(() -> processors.onCommand(valueType, intent, processor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("StorageOrdinalAware")
        .hasMessageContaining(valueType.name());
  }

  private static Stream<Arguments> ordinalCommands() {
    return Stream.of(
        arguments(ValueType.JOB, JobIntent.COMPLETE),
        arguments(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL),
        arguments(ValueType.USER_TASK, UserTaskIntent.COMPLETE),
        arguments(ValueType.TIMER, TimerIntent.TRIGGER),
        arguments(ValueType.INCIDENT, IncidentIntent.RESOLVE),
        arguments(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATE),
        arguments(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionIntent.CORRELATE));
  }

  private static TypedRecordProcessor<JobRecord> plainJobProcessor() {
    return new TypedRecordProcessor<>() {
      @Override
      public void processRecord(final TypedRecord<JobRecord> record) {}
    };
  }

  private static TypedRecordProcessor<JobRecord> awareJobProcessor() {
    return new AwareJobProcessor();
  }

  private static TypedRecordProcessor<UnifiedRecordValue> plainProcessor() {
    return new TypedRecordProcessor<>() {
      @Override
      public void processRecord(final TypedRecord<UnifiedRecordValue> record) {}
    };
  }

  private static TypedRecordProcessor<DeploymentRecord> plainDeploymentProcessor() {
    return new TypedRecordProcessor<>() {
      @Override
      public void processRecord(final TypedRecord<DeploymentRecord> record) {}
    };
  }

  private static final class AwareJobProcessor
      implements TypedRecordProcessor<JobRecord>, StorageOrdinalAware<JobRecord> {

    @Override
    public void processRecord(final TypedRecord<JobRecord> record) {}
  }
}
