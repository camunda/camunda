/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import java.util.List;

/**
 * Holds the authorization filter data for audit log queries. This record encapsulates the composite
 * authorization structure for audit logs, matching the Elasticsearch implementation in {@code
 * AuditLogFilterTransformer.toAuthorizationCheckSearchQuery}.
 *
 * @param authorizedCategories categories authorized via AUDIT_LOG resource type
 * @param authorizedProcessDefinitionIdsForProcessInstance process definition IDs authorized for
 *     READ_PROCESS_INSTANCE
 * @param hasProcessInstanceWildcard true if user has wildcard access for READ_PROCESS_INSTANCE
 * @param authorizedProcessDefinitionIdsForUserTask process definition IDs authorized for
 *     READ_USER_TASK
 * @param hasUserTaskWildcard true if user has wildcard access for READ_USER_TASK
 */
public record AuditLogAuthorizationFilter(
    List<String> authorizedCategories,
    List<String> authorizedProcessDefinitionIdsForProcessInstance,
    boolean hasProcessInstanceWildcard,
    List<String> authorizedProcessDefinitionIdsForUserTask,
    boolean hasUserTaskWildcard) {

  public static AuditLogAuthorizationFilter disabled() {
    return new AuditLogAuthorizationFilter(List.of(), List.of(), false, List.of(), false);
  }

  public boolean hasAnyAuthorization() {
    return !authorizedCategories.isEmpty()
        || !authorizedProcessDefinitionIdsForProcessInstance.isEmpty()
        || hasProcessInstanceWildcard
        || !authorizedProcessDefinitionIdsForUserTask.isEmpty()
        || hasUserTaskWildcard;
  }
}
