/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.form.EmbeddedFormBatch;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class EmbeddedFormHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-form";
  private final EmbeddedFormHandler underTest = new EmbeddedFormHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EmbeddedFormBatch.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<Process> processCreatedRecord =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(ProcessIntent.CREATED));

    final Record<Process> processDeletedRecord =
        factory.generateRecord(ValueType.PROCESS, r -> r.withIntent(ProcessIntent.DELETED));

    // when - then
    assertThat(underTest.handlesRecord(processCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(processDeletedRecord)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final var expectedId = 123L;

    final var processRecordValue =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(expectedId)
            .build();

    final Record<Process> processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(processRecordValue));

    // when
    final var idList = underTest.generateIds(processRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final var inputEntity = new FormEntity().setId("my-form");
    final var batch = new EmbeddedFormBatch().setId("111");
    batch.setForms(Collections.singletonList(inputEntity));

    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(batch, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldAddMultipleEntitiesOnFlush() {
    // given
    final var firstInputEntity = new FormEntity().setId("my-form-one");
    final var secondInputEntity = new FormEntity().setId("my-form-two");
    final var batch = new EmbeddedFormBatch().setId("111");
    batch.setForms(Arrays.asList(firstInputEntity, secondInputEntity));

    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(batch, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, firstInputEntity);
    verify(mockRequest, times(1)).add(indexName, secondInputEntity);
  }

  @Test
  void shouldNotAddEntityOnFlush() {
    // given
    final var inputEntity = new EmbeddedFormBatch().setId("111");

    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verifyNoInteractions(mockRequest);
  }

  @Test
  void shouldCreateEntityFromRecord() throws IOException {
    // given
    final var processDefinitionKey = 123L;
    final var resource =
        getClass().getClassLoader().getResource("process/one-embedded-form-process.bpmn");
    assertThat(resource).isNotNull();
    final var processRecordValue =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("testProcessId")
            .withResource(Files.readAllBytes(Path.of(resource.getPath())))
            .withTenantId("my-tenant")
            .build();

    final Record<Process> processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(processRecordValue));

    // when
    final var embeddedFormBatch = new EmbeddedFormBatch().setId("id");
    underTest.updateEntity(processRecord, embeddedFormBatch);

    // then
    assertThat(embeddedFormBatch.getForms()).isNotNull();
    assertThat(embeddedFormBatch.getForms()).hasSize(1);

    final var formEntity = embeddedFormBatch.getForms().getFirst();
    assertThat(formEntity.getId())
        .isEqualTo(String.format("%s_%s", processDefinitionKey, "my-embedded-form"));
    assertThat(formEntity.getFormId()).isEqualTo("my-embedded-form");
    assertThat(formEntity.getSchema()).isEqualTo(formJson());
    assertThat(formEntity.getTenantId()).isEqualTo("my-tenant");
    assertThat(formEntity.getProcessDefinitionId()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(formEntity.getEmbedded()).isTrue();
    assertThat(formEntity.getIsDeleted()).isFalse();
  }

  @Test
  void shouldCreateMultipleEntitiesFromRecord() throws IOException {
    // given
    final var processDefinitionKey = 123L;
    final var resource =
        getClass().getClassLoader().getResource("process/two-embedded-form-process.bpmn");
    assertThat(resource).isNotNull();
    final var processRecordValue =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("testProcessId")
            .withResource(Files.readAllBytes(Path.of(resource.getPath())))
            .build();

    final Record<Process> processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(processRecordValue));

    // when
    final var embeddedFormBatch = new EmbeddedFormBatch().setId("id");
    underTest.updateEntity(processRecord, embeddedFormBatch);

    // then
    assertThat(embeddedFormBatch.getForms()).isNotNull();
    assertThat(embeddedFormBatch.getForms()).hasSize(2);

    final var formEntityIds =
        embeddedFormBatch.getForms().stream().map(FormEntity::getFormId).toList();
    assertThat(formEntityIds).containsExactly("my-embedded-form-one", "my-embedded-form-two");
  }

  @Test
  void shouldCreateOnlyOneFromRecordWithMultipleProcesses() throws IOException {
    // given
    final var processDefinitionKey = 123L;
    final var resource =
        getClass().getClassLoader().getResource("process/two-process-with-embedded-form.bpmn");
    assertThat(resource).isNotNull();
    final var processRecordValue =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("testProcessIdOne")
            .withResource(Files.readAllBytes(Path.of(resource.getPath())))
            .build();

    final Record<Process> processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(processRecordValue));

    // when
    final var embeddedFormBatch = new EmbeddedFormBatch().setId("id");
    underTest.updateEntity(processRecord, embeddedFormBatch);

    // then
    assertThat(embeddedFormBatch.getForms()).isNotNull();
    assertThat(embeddedFormBatch.getForms()).hasSize(1);

    final var formEntity = embeddedFormBatch.getForms().getFirst();
    assertThat(formEntity.getId())
        .isEqualTo(String.format("%s_%s", processDefinitionKey, "my-embedded-form-one"));
    assertThat(formEntity.getFormId()).isEqualTo("my-embedded-form-one");
    assertThat(formEntity.getSchema()).isEqualTo(formJson());
    assertThat(formEntity.getProcessDefinitionId()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(formEntity.getEmbedded()).isTrue();
    assertThat(formEntity.getIsDeleted()).isFalse();
  }

  @Test
  void shouldNotCreateEntityFromRecord() throws IOException {
    // given
    final var processDefinitionKey = 123L;
    // BPMN contains no embedded forms
    final var resource = getClass().getClassLoader().getResource("process/test-process.bpmn");
    assertThat(resource).isNotNull();
    final var processRecordValue =
        ImmutableProcess.builder()
            .from(factory.generateObject(ImmutableProcess.class))
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("testProcessId")
            .withResource(Files.readAllBytes(Path.of(resource.getPath())))
            .build();

    final Record<Process> processRecord =
        factory.generateRecord(
            ValueType.PROCESS,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(processRecordValue));

    // when
    final var embeddedFormBatch = new EmbeddedFormBatch().setId("id");
    underTest.updateEntity(processRecord, embeddedFormBatch);

    // then
    assertThat(embeddedFormBatch.getForms()).isNull();
  }

  private String formJson() {
    return """
{
  "components": [
    {
      "label": "Text field",
      "type": "textfield",
      "layout": {
        "row": "Row_14zv75q",
        "columns": null
      },
      "id": "Field_0h71kwj",
      "key": "textfield_t43iqk"
    }
  ],
  "type": "default",
  "id": "my-form",
  "executionPlatform": "Camunda Cloud",
  "executionPlatformVersion": "8.6.0",
  "versionTag": "3",
  "exporter": {
    "name": "Camunda Modeler",
    "version": "5.28.0"
  },
  "schemaVersion": 16
}""";
  }
}
