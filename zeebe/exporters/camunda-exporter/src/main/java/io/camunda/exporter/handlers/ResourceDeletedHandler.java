/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.resource.DeployedResourceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import java.util.List;

public class ResourceDeletedHandler implements ExportHandler<DeployedResourceEntity, Resource> {

  private static final ResourceIntent SUPPORTED_INTENT = ResourceIntent.DELETED;
  private final String indexName;

  public ResourceDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.RESOURCE;
  }

  @Override
  public Class<DeployedResourceEntity> getEntityType() {
    return DeployedResourceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<Resource> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENT.equals(record.getIntent())
        && isRpaResource(record.getValue().getResourceName());
  }

  @Override
  public List<String> generateIds(final Record<Resource> record) {
    return List.of(String.valueOf(record.getValue().getResourceKey()));
  }

  @Override
  public DeployedResourceEntity createNewEntity(final String id) {
    return new DeployedResourceEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<Resource> record, final DeployedResourceEntity entity) {
    // no-op since the entity will be deleted
  }

  @Override
  public void flush(final DeployedResourceEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private static boolean isRpaResource(final String resourceName) {
    return resourceName != null && resourceName.endsWith(".rpa");
  }
}
