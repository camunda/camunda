/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

import io.camunda.webapps.schema.entities.JoinRelationshipType;

public enum AuditLogJoinRelationshipType implements JoinRelationshipType {
  BATCH_OPERATION("batchOperation"),
  BATCH_ITEM("batchItem");

  private final String type;

  AuditLogJoinRelationshipType(final String type) {
    this.type = type;
  }

  @Override
  public String getType() {
    return type;
  }
}
