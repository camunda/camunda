/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.form.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FormHandler implements ExportHandler<FormEntity, Form> {

  private final String indexName;
  private final ExporterEntityCache<String, CachedFormEntity> formCache;

  public FormHandler(
      final String indexName, final ExporterEntityCache<String, CachedFormEntity> formCache) {
    this.indexName = indexName;
    this.formCache = formCache;
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
    return List.of(String.valueOf(record.getValue().getFormKey()));
  }

  @Override
  public FormEntity createNewEntity(final String id) {
    return new FormEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Form> record, final FormEntity entity) {
    final Form value = record.getValue();
    final var isDeleted = record.getIntent().equals(FormIntent.DELETED);

    entity
        .setVersion((long) value.getVersion())
        .setKey(value.getFormKey())
        .setFormId(value.getFormId())
        .setSchema(new String(value.getResource(), StandardCharsets.UTF_8))
        .setTenantId(value.getTenantId())
        .setEmbedded(false)
        .setIsDeleted(isDeleted);

    if (!isDeleted) {
      // update local cache so that the form info is available immediately
      final var cachedFormEntity = new CachedFormEntity(entity.getFormId(), entity.getVersion());
      formCache.put(entity.getId(), cachedFormEntity);
    } else {
      formCache.remove(entity.getId());
    }
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
