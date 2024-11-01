/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.tasklist.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FormHandler implements ExportHandler<FormEntity, Form> {

  private final String indexName;

  public FormHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.FORM;
  }

  @Override
  public Class<FormEntity> getEntityType() {
    return FormEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<Form> record) {
    return getHandledValueType().equals(record.getValueType());
  }

  @Override
  public List<String> generateIds(final Record<Form> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public FormEntity createNewEntity(final String id) {
    return new FormEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Form> record, final FormEntity entity) {
    final Form value = record.getValue();
    entity
        .setVersion((long) value.getVersion())
        .setKey(value.getFormKey())
        .setBpmnId(value.getFormId())
        .setSchema(new String(value.getResource(), StandardCharsets.UTF_8))
        .setTenantId(value.getTenantId())
        .setIsDeleted(record.getIntent().name().equals(FormIntent.DELETED.name()));
  }

  @Override
  public void flush(final FormEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
