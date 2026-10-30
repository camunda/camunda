/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public final class VariablesMetadataTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static Map<String, VariableMetadataRecord> redact(final String name) {
    return Map.of(
        name, new VariableMetadataRecord().setProtectionModes(Set.of(ProtectionMode.REDACT)));
  }

  @Test
  public void shouldCreateVariableWithMetadataOnProcessInstanceCreation() {
    // given
    final var processId = "process-" + UUID.randomUUID();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(UUID.randomUUID().toString()))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("secret", "s3cr3t", "plain", "value"))
            .withVariablesMetadata(redact("secret"))
            .create();

    // then
    final List<Record<VariableRecordValue>> variables =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .toList();
    assertThat(variables)
        .extracting(
            r -> r.getValue().getName(), r -> r.getValue().getMetadata().getProtectionModes())
        .containsExactlyInAnyOrder(
            tuple("secret", Set.of(ProtectionMode.REDACT)), tuple("plain", Set.of()));
  }

  @Test
  public void shouldReturnVariablesMetadataInCreationResponseButNotInCreatedEvent() {
    // given
    final var processId = "process-" + UUID.randomUUID();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(UUID.randomUUID().toString()))
                .endEvent()
                .done())
        .deploy();
    final var responses = new ConcurrentHashMap<Long, ProcessInstanceCreationRecord>();
    final var responseWriter = ENGINE.getCommandResponseWriter();
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  if (invocation.getArgument(0)
                      instanceof final ProcessInstanceCreationRecord creation) {
                    // copy the record: engine record objects are reused across commands
                    final var copy = new ProcessInstanceCreationRecord();
                    final var buffer = new UnsafeBuffer(new byte[creation.getLength()]);
                    creation.write(buffer, 0);
                    copy.wrap(buffer);
                    responses.put(copy.getProcessInstanceKey(), copy);
                  }
                  return responseWriter;
                })
        .when(responseWriter)
        .valueWriter(any());

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("secret", "s3cr3t"))
            .withVariablesMetadata(redact("secret"))
            .create();

    // then
    Awaitility.await("until the creation response is written")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(responses).containsKey(processInstanceKey));
    final var response = responses.get(processInstanceKey);
    assertThat(response.getVariables()).containsEntry("secret", "s3cr3t");
    assertThat(response.getVariablesMetadata())
        .containsOnlyKeys("secret")
        .allSatisfy(
            (name, metadata) ->
                assertThat(metadata.getProtectionModes()).containsExactly(ProtectionMode.REDACT));

    final var created =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .withInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(created.getValue().getVariables()).isEmpty();
    assertThat(created.getValue().getVariablesMetadata()).isEmpty();
  }

  @Test
  public void shouldCreateVariableWithMetadataOnVariableDocumentUpdate() {
    // given
    final var processId = "process-" + UUID.randomUUID();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(UUID.randomUUID().toString()))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long taskKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .variables()
        .ofScope(taskKey)
        .withDocument(Map.of("secret", "s3cr3t"))
        .withMetadata(redact("secret"))
        .withLocalSemantic()
        .update();

    // then
    final var variable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("secret")
            .getFirst();
    assertThat(variable.getValue().getMetadata().getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
  }
}
