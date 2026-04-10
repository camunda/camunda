/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.resource.ResourceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import java.util.List;

public class ResourceHandler implements ExportHandler<ResourceEntity, Resource> {

  private final String indexName;

  public ResourceHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.RESOURCE;
  }

  @Override
  public Class<ResourceEntity> getEntityType() {
    return ResourceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<Resource> record) {
    return getHandledValueType().equals(record.getValueType());
  }

  @Override
  public List<String> generateIds(final Record<Resource> record) {
    return List.of(String.valueOf(record.getValue().getResourceKey()));
  }

  @Override
  public ResourceEntity createNewEntity(final String id) {
    return new ResourceEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Resource> record, final ResourceEntity entity) {
    final Resource value = record.getValue();
    final var isDeleted = record.getIntent().equals(ResourceIntent.DELETED);

    entity
        .setKey(value.getResourceKey())
        .setResourceId(value.getResourceId())
        .setVersion(value.getVersion())
        .setVersionTag(value.getVersionTag())
        .setResourceName(value.getResourceName())
        .setResource(value.getResourceProp())
        .setTenantId(value.getTenantId())
        .setDeploymentKey(value.getDeploymentKey())
        .setIsDeleted(isDeleted);
  }

  @Override
  public void flush(final ResourceEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
