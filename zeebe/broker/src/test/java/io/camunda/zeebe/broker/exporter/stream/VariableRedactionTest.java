/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that protected values are redacted before the exporter director hands records to any
 * exporter. Redaction of further record types is covered here as it is added.
 */
public final class VariableRedactionTest {

  @Rule public final ExporterRule rule = ExporterRule.activeExporter();

  private ControlledTestExporter exporter;
  private ExporterDescriptor descriptor;

  @Before
  public void init() {
    exporter = spy(new ControlledTestExporter());
    descriptor =
        spy(new ExporterDescriptor("exporter-1", exporter.getClass(), Collections.emptyMap()));
    doAnswer(c -> exporter).when(descriptor).newInstance();
  }

  @Test
  public void shouldRedactValueOfVariableProtectedWithRedact() {
    // given
    final var exportedValues = captureExportedVariableValues();
    rule.startExporterDirector(List.of(descriptor));

    // when
    rule.writeEvent(
        VariableIntent.CREATED,
        variableRecord("secret", "\"hunter2\"", Set.of(ProtectionMode.REDACT)));

    // then
    Awaitility.await("variable is exported")
        .untilAsserted(() -> assertThat(exportedValues).hasSize(1));
    assertThat(exportedValues).containsExactly((String) null);
  }

  @Test
  public void shouldNotChangeValueOfUnprotectedVariable() {
    // given
    final var exportedValues = captureExportedVariableValues();
    rule.startExporterDirector(List.of(descriptor));

    // when
    rule.writeEvent(VariableIntent.CREATED, variableRecord("plain", "\"visible\"", Set.of()));

    // then
    Awaitility.await("variable is exported")
        .untilAsserted(() -> assertThat(exportedValues).hasSize(1));
    assertThat(exportedValues).containsExactly("\"visible\"");
  }

  /** Captures the value as handed to the exporter, before the test exporter copies the record. */
  private List<String> captureExportedVariableValues() {
    final var values = new CopyOnWriteArrayList<String>();
    doAnswer(
            invocation -> {
              final Record<?> record = invocation.getArgument(0);
              values.add(((VariableRecordValue) record.getValue()).getValue());
              return invocation.callRealMethod();
            })
        .when(exporter)
        .export(any());
    return values;
  }

  private static VariableRecord variableRecord(
      final String name, final String jsonValue, final Set<ProtectionMode> modes) {
    final var variable =
        new VariableRecord()
            .setName(BufferUtil.wrapString(name))
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(jsonValue)))
            .setScopeKey(1L)
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId(BufferUtil.wrapString("process"));
    variable.getMetadata().setProtectionModes(modes);
    return variable;
  }
}
