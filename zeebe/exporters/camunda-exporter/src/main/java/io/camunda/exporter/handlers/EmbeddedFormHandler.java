/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.form.EmbeddedFormBatch;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import io.camunda.zeebe.util.modelreader.ProcessModelReader.EmbeddedForm;
import java.util.List;
import java.util.Optional;

public class EmbeddedFormHandler implements ExportHandler<EmbeddedFormBatch, Process> {

  private static final String FORM_ID_PATTERN = "%s_%s";
  private final String indexName;

  public EmbeddedFormHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS;
  }

  @Override
  public Class<EmbeddedFormBatch> getEntityType() {
    return EmbeddedFormBatch.class;
  }

  @Override
  public boolean handlesRecord(final Record<Process> record) {
    return record.getIntent().equals(ProcessIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<Process> record) {
    return List.of(String.valueOf(record.getValue().getProcessDefinitionKey()));
  }

  @Override
  public EmbeddedFormBatch createNewEntity(final String id) {
    return new EmbeddedFormBatch().setId(id);
  }

  @Override
  public void updateEntity(final Record<Process> record, final EmbeddedFormBatch entity) {
    final var value = record.getValue();
    final var resource = value.getResource();
    final var bpmnProcessId = value.getBpmnProcessId();
    ProcessModelReader.of(resource, bpmnProcessId)
        .flatMap(ProcessModelReader::extractEmbeddedForms)
        .map(l -> mapToFormEntities(record, l))
        .ifPresent(entity::setForms);
  }

  @Override
  public void flush(final EmbeddedFormBatch entity, final BatchRequest batchRequest) {
    final var forms = entity.getForms();
    Optional.ofNullable(forms).ifPresent(l -> l.forEach(f -> batchRequest.add(indexName, f)));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private List<FormEntity> mapToFormEntities(
      final Record<Process> record, final List<EmbeddedForm> embeddedForms) {
    final var value = record.getValue();
    final var tenantId = value.getTenantId();
    final var processDefinitionKey = String.valueOf(value.getProcessDefinitionKey());
    return embeddedForms.stream()
        .map(f -> mapToFormEntity(f, processDefinitionKey, tenantId))
        .toList();
  }

  private FormEntity mapToFormEntity(
      final EmbeddedForm embeddedForm, final String processDefinitionKey, final String tenantId) {
    final var formId = embeddedForm.id();
    final var schema = embeddedForm.schema();
    final var formEntityId = String.format(FORM_ID_PATTERN, processDefinitionKey, formId);
    return new FormEntity()
        .setId(formEntityId)
        .setFormId(formId)
        .setSchema(schema)
        .setTenantId(tenantId)
        .setProcessDefinitionId(processDefinitionKey)
        .setEmbedded(true)
        .setIsDeleted(false);
  }
}
