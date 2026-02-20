/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUDIT_LOG;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD_CHAR;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;

import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizedAuditLogCategoryType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Holds the authorization filter data for audit log queries. This record encapsulates the composite
 * authorization structure for audit logs, matching the Elasticsearch implementation in {@code
 * AuditLogFilterTransformer.toAuthorizationCheckSearchQuery}.
 *
 * <p>Wildcard access is determined by the presence of "*" in the respective lists, rather than
 * using separate boolean flags.
 *
 * @param authorizedCategories categories authorized via AUDIT_LOG property-based authorization (the
 *     "category" property)
 * @param authorizedProcessDefinitionIdsForProcessInstance process definition IDs authorized for
 *     READ_PROCESS_INSTANCE (may contain "*" for wildcard access)
 * @param authorizedProcessDefinitionIdsForUserTask process definition IDs authorized for
 *     READ_USER_TASK (may contain "*" for wildcard access)
 */
public record AuditLogAuthorizationFilter(
    List<String> authorizedCategories,
    List<String> authorizedProcessDefinitionIdsForProcessInstance,
    List<String> authorizedProcessDefinitionIdsForUserTask) {

  /**
   * Returns a filter that blocks all access. Use this as a defensive default to ensure no data is
   * returned when authorization information is missing or invalid.
   */
  public static AuditLogAuthorizationFilter denyAll() {
    return new AuditLogAuthorizationFilter(List.of(), List.of(), List.of());
  }

  public static AuditLogAuthorizationFilter allowAll() {
    return new AuditLogAuthorizationFilter(
        List.of(WILDCARD_CHAR), List.of(WILDCARD_CHAR), List.of(WILDCARD_CHAR));
  }

  /**
   * Builds the authorization filter for audit log queries by extracting the composite authorization
   * structure from ResourceAccessChecks.
   *
   * <p>This mirrors the Elasticsearch implementation in {@code
   * AuditLogFilterTransformer.toAuthorizationCheckSearchQuery}, supporting:
   *
   * <ul>
   *   <li>AUDIT_LOG resource type: property based authorized categories
   *   <li>PROCESS_DEFINITION + READ_PROCESS_INSTANCE: authorized process definition IDs or wildcard
   *       access
   *   <li>PROCESS_DEFINITION + READ_USER_TASK: authorized process definition IDs for user tasks or
   *       wildcard access
   * </ul>
   */
  public static AuditLogAuthorizationFilter of(final ResourceAccessChecks resourceAccessChecks) {
    final var check = resourceAccessChecks.authorizationCheck();
    if (!check.enabled()) {
      // When authorization is disabled, grant full access via wildcards
      return allowAll();
    }

    final List<String> authorizedCategories = new ArrayList<>();
    final List<String> processDefIdsForProcessInstance = new ArrayList<>();
    final List<String> processDefIdsForUserTask = new ArrayList<>();

    applyResourceIdBasedAuthorization(
        check.authorizations(), processDefIdsForProcessInstance, processDefIdsForUserTask);
    applyPropertyBasedAuthorization(resourceAccessChecks, authorizedCategories);

    return new AuditLogAuthorizationFilter(
        authorizedCategories, processDefIdsForProcessInstance, processDefIdsForUserTask);
  }

  private static void applyResourceIdBasedAuthorization(
      final List<Authorization<?>> authorizations,
      final List<String> processDefIdsForProcessInstance,
      final List<String> processDefIdsForUserTask) {
    for (final var auth : authorizations) {
      if (auth == null || auth.resourceType() == null || !auth.hasAnyResourceIds()) {
        continue;
      }

      if (PROCESS_DEFINITION.equals(auth.resourceType())) {
        if (READ_PROCESS_INSTANCE.equals(auth.permissionType())) {
          processDefIdsForProcessInstance.addAll(auth.resourceIds());
        } else if (READ_USER_TASK.equals(auth.permissionType())) {
          processDefIdsForUserTask.addAll(auth.resourceIds());
        }
      }
    }
  }

  private static void applyPropertyBasedAuthorization(
      final ResourceAccessChecks resourceAccessChecks, final List<String> authorizedCategories) {
    final var auditLogPropertyNames =
        resourceAccessChecks
            .getAuthorizedResourcePropertyNamesByType()
            .getOrDefault(AUDIT_LOG.name(), Set.of());
    if (auditLogPropertyNames.contains(Authorization.PROP_CATEGORY)) {
      authorizedCategories.addAll(AuthorizedAuditLogCategoryType.getAuthorizedCategories());
    }
  }

  /** Returns true if the user has wildcard access for READ_PROCESS_INSTANCE permission. */
  public boolean hasProcessInstanceWildcard() {
    return authorizedProcessDefinitionIdsForProcessInstance.contains(WILDCARD_CHAR);
  }

  /** Returns true if the user has wildcard access for audit log categories. */
  public boolean hasCategoryWildcard() {
    return authorizedCategories.contains(WILDCARD_CHAR);
  }

  /** Returns true if the user has wildcard access for READ_USER_TASK permission. */
  public boolean hasUserTaskWildcard() {
    return authorizedProcessDefinitionIdsForUserTask.contains(WILDCARD_CHAR);
  }
}
