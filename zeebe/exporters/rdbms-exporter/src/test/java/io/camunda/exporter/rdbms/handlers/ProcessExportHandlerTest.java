/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class ProcessExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessDefinitionWriter processDefinitionWriter =
      mock(ProcessDefinitionWriter.class);

  @SuppressWarnings("unchecked")
  private final ProcessExportHandler underTest =
      new ProcessExportHandler(
          processDefinitionWriter,
          mock(ExporterEntityCache.class),
          mock(ExtensionPropertyConfiguration.class));

  @ParameterizedTest
  @EnumSource(
      value = ProcessIntent.class,
      names = {"CREATED", "DRAINING", "DELETED"},
      mode = Mode.INCLUDE)
  void shouldExportHandledIntents(final ProcessIntent intent) {
    // given
    final Record<Process> record =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.canExport(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessIntent.class,
      names = {"CREATED", "DRAINING", "DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotExportOtherIntents(final ProcessIntent intent) {
    // given
    final Record<Process> record =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.canExport(record)).isFalse();
  }

  @Test
  void shouldMarkDrainingOnDrainingRecord() {
    // given
    final long processDefinitionKey = 123L;
    final Process value =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(processDefinitionKey)
            .build();
    final Record<Process> record =
        factory.generateRecord(
            ValueType.PROCESS, r -> r.withIntent(ProcessIntent.DRAINING).withValue(value));

    // when
    underTest.export(record);

    // then
    verify(processDefinitionWriter).markDraining(processDefinitionKey);
  }

  @Test
  void shouldMarkDeletedOnDeletedRecord() {
    // given
    final long processDefinitionKey = 456L;
    final Process value =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(processDefinitionKey)
            .build();
    final Record<Process> record =
        factory.generateRecord(
            ValueType.PROCESS, r -> r.withIntent(ProcessIntent.DELETED).withValue(value));

    // when
    underTest.export(record);

    // then
    verify(processDefinitionWriter).markDeleted(processDefinitionKey);
  }
}
