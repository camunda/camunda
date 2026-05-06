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
import io.camunda.zeebe.exporter.common.utils.ResourceUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import java.util.List;

public class ResourceCreatedHandler implements ExportHandler<DeployedResourceEntity, Resource> {

  private static final List<ResourceIntent> SUPPORTED_INTENTS =
      List.of(ResourceIntent.CREATED, ResourceIntent.REEXPORTED);
  private final String indexName;

  public ResourceCreatedHandler(final String indexName) {
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
        && SUPPORTED_INTENTS.contains(record.getIntent());
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
    final Resource value = record.getValue();
    entity
        .setResourceKey(value.getResourceKey())
        .setResourceId(value.getResourceId())
        .setResourceName(value.getResourceName())
        .setResourceType(ResourceUtils.deriveResourceType(value.getResourceName()))
        .setVersion(value.getVersion())
        .setVersionTag(value.getVersionTag())
        .setDeploymentKey(value.getDeploymentKey())
        .setTenantId(value.getTenantId())
        .setResourceContent(value.getResourceProp());
  }

  @Override
  public void flush(final DeployedResourceEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
