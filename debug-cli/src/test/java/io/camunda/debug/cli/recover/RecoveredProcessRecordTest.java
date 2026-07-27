/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import static io.camunda.debug.cli.recover.RecoverTestSupport.asString;
import static io.camunda.debug.cli.recover.RecoverTestSupport.persistedProcess;
import static io.camunda.debug.cli.recover.RecoverTestSupport.readResource;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class RecoveredProcessRecordTest {

  private static final String RESOURCE = "recover/simple-process.bpmn";

  @Test
  void shouldReconstructRecordFromPersistedProcess() throws IOException {
    // given
    final var resource = readResource(RESOURCE);
    final var persisted =
        persistedProcess(
            123L,
            "testProcessId",
            2,
            "processTag",
            "tenant-a",
            "simple.bpmn",
            resource,
            PersistedProcessState.ACTIVE);

    // when
    final var record = RecoveredProcessRecord.from(persisted);

    // then
    assertThat(record.getIntent()).isEqualTo(ProcessIntent.CREATED);
    assertThat(record.getValueType()).isEqualTo(ValueType.PROCESS);
    assertThat(record.getKey()).isEqualTo(123L);

    final Process value = record.getValue();
    assertThat(value.getProcessDefinitionKey()).isEqualTo(123L);
    assertThat(value.getBpmnProcessId()).isEqualTo("testProcessId");
    assertThat(value.getVersion()).isEqualTo(2);
    assertThat(value.getVersionTag()).isEqualTo("processTag");
    assertThat(value.getTenantId()).isEqualTo("tenant-a");
    assertThat(value.getResourceName()).isEqualTo("simple.bpmn");
    assertThat(asString(value.getResource())).isEqualTo(asString(resource));
  }

  @Test
  void shouldProduceSameProcessEntityAsRealHandlerReads() throws IOException {
    // given — the anti-drift guarantee: driving the real ProcessHandler off a record reconstructed
    // from primary storage yields a document carrying exactly the persisted-process data.
    final var resource = readResource(RESOURCE);
    final var persisted =
        persistedProcess(
            456L,
            "testProcessId",
            3,
            "processTag",
            "<default>",
            "simple.bpmn",
            resource,
            PersistedProcessState.ACTIVE);
    final var handler =
        new ProcessHandler("process", new NoopProcessCache(), new ExtensionPropertyConfiguration());
    final var record = RecoveredProcessRecord.from(persisted);

    // when
    final var entity = new ProcessEntity();
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo("456");
    assertThat(entity.getKey()).isEqualTo(456L);
    assertThat(entity.getBpmnProcessId()).isEqualTo("testProcessId");
    assertThat(entity.getVersion()).isEqualTo(3);
    assertThat(entity.getVersionTag()).isEqualTo("processTag");
    assertThat(entity.getTenantId()).isEqualTo("<default>");
    assertThat(entity.getResourceName()).isEqualTo("simple.bpmn");
    assertThat(entity.getName()).isEqualTo("testProcessName");
    assertThat(entity.getBpmnXml()).isEqualTo(asString(resource));
  }
}
