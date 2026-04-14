/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.ResourceDbModel;
import io.camunda.db.rdbms.write.domain.ResourceDbModel.ResourceDbModelBuilder;
import io.camunda.db.rdbms.write.service.ResourceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceExportHandler implements RdbmsExportHandler<Resource> {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceExportHandler.class);

  private static final Set<ResourceIntent> EXPORTABLE_INTENTS =
      Set.of(ResourceIntent.CREATED, ResourceIntent.DELETED);

  private final ResourceWriter resourceWriter;

  public ResourceExportHandler(final ResourceWriter resourceWriter) {
    this.resourceWriter = resourceWriter;
  }

  @Override
  public boolean canExport(final Record<Resource> record) {
    return record.getValueType() == ValueType.RESOURCE
        && record.getIntent() instanceof final ResourceIntent intent
        && EXPORTABLE_INTENTS.contains(intent)
        && isRpaResource(record.getValue().getResourceName());
  }

  @Override
  public void export(final Record<Resource> record) {
    switch (record.getIntent()) {
      case ResourceIntent.CREATED -> resourceWriter.create(map(record));
      case ResourceIntent.DELETED -> resourceWriter.delete(record.getValue().getResourceKey());
      default -> LOG.warn("Unexpected intent {} for resource record", record.getIntent());
    }
  }

  private ResourceDbModel map(final Record<Resource> record) {
    final var value = record.getValue();
    return new ResourceDbModelBuilder()
        .resourceKey(value.getResourceKey())
        .resourceId(value.getResourceId())
        .resourceName(value.getResourceName())
        .version(value.getVersion())
        .versionTag(value.getVersionTag())
        .deploymentKey(value.getDeploymentKey())
        .tenantId(value.getTenantId())
        .build();
  }

  private static boolean isRpaResource(final String resourceName) {
    return resourceName != null && resourceName.endsWith(".rpa");
  }
}
