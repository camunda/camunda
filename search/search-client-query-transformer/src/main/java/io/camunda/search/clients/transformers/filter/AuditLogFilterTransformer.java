/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.*;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUDIT_LOG;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import java.util.Optional;

public class AuditLogFilterTransformer extends IndexFilterTransformer<AuditLogFilter> {

  public AuditLogFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final AuditLogFilter filter) {
    return and(
        stringOperations(ID, filter.auditLogKeyOperations()),
        stringOperations(ACTOR_ID, filter.actorIdOperations()),
        stringOperations(ACTOR_TYPE, filter.actorTypeOperations()),
        longOperations(BATCH_OPERATION_KEY, filter.batchOperationKeyOperations()),
        stringOperations(CATEGORY, filter.categoryOperations()),
        longOperations(DECISION_DEFINITION_KEY, filter.decisionDefinitionKeyOperations()),
        longOperations(DECISION_EVALUATION_KEY, filter.decisionEvaluationKeyOperations()),
        longOperations(DEPLOYMENT_KEY, filter.deploymentKeyOperations()),
        longOperations(ELEMENT_INSTANCE_KEY, filter.elementInstanceKeyOperations()),
        stringOperations(ENTITY_KEY, filter.entityKeyOperations()),
        stringOperations(ENTITY_TYPE, filter.entityTypeOperations()),
        longOperations(FORM_KEY, filter.formKeyOperations()),
        longOperations(JOB_KEY, filter.jobKeyOperations()),
        stringOperations(OPERATION_TYPE, filter.operationTypeOperations()),
        stringOperations(PROCESS_DEFINITION_ID, filter.processDefinitionIdOperations()),
        longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()),
        longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()),
        longOperations(RESOURCE_KEY, filter.resourceKeyOperations()),
        stringOperations(RESULT, filter.resultOperations()),
        stringOperations(TENANT_ID, filter.tenantIdOperations()),
        dateTimeOperations(TIMESTAMP, filter.timestampOperations()),
        longOperations(USER_TASK_KEY, filter.userTaskKeyOperations()));
  }

  /*
   * Audit logs can have either a specific tenant ID or a global tenant scope. For example, Identity
   * related operations (ex. group, role entity assignment) are logged with a global tenant scope.
   * Process-related audit logs are logged with a specific tenant ID.
   *
   * This method creates a customized tenant check query for audit logs. It checks if the audit log
   * entry belongs to either the specified tenant IDs or has a global tenant scope.
   *
   * @param tenantCheck the tenant check details
   * @param field the field to apply the tenant check on
   * @return the constructed search query for tenant checks
   */
  @Override
  protected SearchQuery toTenantCheckSearchQuery(
      final TenantCheck tenantCheck, final String field) {
    final var tenantCheckQuery =
        Optional.of(tenantCheck)
            .map(TenantCheck::tenantIds)
            .filter(t -> !t.isEmpty())
            .map(t -> stringTerms(field, t))
            .orElse(null);

    final var matchGlobalQuery = term(TENANT_SCOPE, AuditLogTenantScope.GLOBAL.name());

    return or(matchGlobalQuery, tenantCheckQuery);
  }

  /*
   * Includes a customized authorization check query for audit logs. Audit logs support composite
   * authorizations, where a user may be authorized to:
   *   - View all audit logs of a given audit log category,
   *   - View all audit logs of a specified process definition
   *   - View audit logs related to user tasks of a specified process definition.
   *
   * As a result, we need to construct an authorization check query that accounts for these scenarios.
   *
   * @param authorization the authorization details
   * @return the constructed search query for authorization checks
   */
  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    final var categoryQuery = stringTerms(CATEGORY, authorization.resourceIds());
    final var processDefinitionQuery =
        stringTerms(PROCESS_DEFINITION_ID, authorization.resourceIds());
    final var userTaskQuery = term(CATEGORY, AuditLogOperationCategory.USER_TASKS.name());
    final var userTaskAuditLogsQuery = and(userTaskQuery, processDefinitionQuery);

    final var resourceType = authorization.resourceType();
    if (AUDIT_LOG.equals(resourceType)) {
      return categoryQuery;
    }
    if (PROCESS_DEFINITION.equals(resourceType)) {
      if (READ_PROCESS_INSTANCE.equals(authorization.permissionType())) {
        return processDefinitionQuery;
      }
      if (READ_USER_TASK.equals(authorization.permissionType())) {
        return userTaskAuditLogsQuery;
      }
    }

    return matchNone();
  }
}
