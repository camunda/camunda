/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;

public class AuditLogCleanupEntity extends AbstractExporterEntity<AuditLogCleanupEntity> {

  // the key by which to delete audit logs
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String key;

  // the AuditLogTemplate field that the key refers to
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String keyField;

  // the type of the audit logs to delete, can be null to delete only by key
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private AuditLogEntityType entityType;

  // the partition id of the index which created the cleanup entry. Used to distribute the cleanup
  // load amongst the partitions.
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private int partitionId;

  public String getKey() {
    return key;
  }

  public AuditLogCleanupEntity setKey(final String key) {
    this.key = key;
    return this;
  }

  public String getKeyField() {
    return keyField;
  }

  public AuditLogCleanupEntity setKeyField(final String keyField) {
    this.keyField = keyField;
    return this;
  }

  public AuditLogEntityType getEntityType() {
    return entityType;
  }

  public AuditLogCleanupEntity setEntityType(final AuditLogEntityType entityType) {
    this.entityType = entityType;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public AuditLogCleanupEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }
}
