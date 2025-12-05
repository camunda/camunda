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
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.webapps.schema.descriptors.template.AuditLogTemplate.*;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class AuditLogFilterTransformer extends IndexFilterTransformer<AuditLogFilter> {

  public AuditLogFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }

  @Override
  public SearchQuery toSearchQuery(final AuditLogFilter filter) {
    return and(
        stringOperations(ACTOR_ID, filter.actorIdOperations()),
        stringOperations(ACTOR_TYPE, filter.actorTypeOperations()),
        stringOperations(AUDIT_LOG_KEY, filter.auditLogKeyOperations()),
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
}
