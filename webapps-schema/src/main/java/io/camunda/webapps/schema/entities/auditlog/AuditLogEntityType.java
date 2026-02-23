/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.auditlog;

public enum AuditLogEntityType {
  UNKNOWN,
  PROCESS_INSTANCE,
  VARIABLE,
  INCIDENT,
  USER_TASK,
  DECISION,
  BATCH,
  USER,
  MAPPING_RULE,
  ROLE,
  GROUP,
  TENANT,
  AUTHORIZATION,
  RESOURCE,
  CLIENT
}
