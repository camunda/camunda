/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.JobEntity;

public enum JobSearchColumn implements SearchColumn<JobEntity> {
  DEADLINE("deadline"),
  DENIED_REASON("deniedReason"),
  ELEMENT_ID("elementId"),
  ELEMENT_INSTANCE_KEY("elementInstanceKey"),
  END_TIME("endTime"),
  ERROR_CODE("errorCode"),
  ERROR_MESSAGE("errorMessage"),
  HAS_FAILED_WITH_RETRIES_LEFT("hasFailedWithRetriesLeft"),
  IS_DENIED("isDenied"),
  JOB_KEY("jobKey"),
  KIND("kind"),
  LISTENER_EVENT_TYPE("listenerEventType"),
  PROCESS_DEFINITION_ID("processDefinitionId"),
  PROCESS_DEFINITION_KEY("processDefinitionKey"),
  PROCESS_INSTANCE_KEY("processInstanceKey"),
  RETRIES("retries"),
  STATE("state"),
  TENANT_ID("tenantId"),
  TYPE("type"),
  WORKER("worker");

  private final String property;

  JobSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<JobEntity> getEntityClass() {
    return JobEntity.class;
  }
}
