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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.ACTOR_ID;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.ACTOR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.AGENT_ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.OPERATION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.PROCESS_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.TIMESTAMP;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.VARIABLE_HISTORY_ITEM_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.VARIABLE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableHistoryTemplate.VARIABLE_OPERATION_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.VariableHistoryItemFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class VariableHistoryItemFilterTransformer
    extends IndexFilterTransformer<VariableHistoryItemFilter> {

  public VariableHistoryItemFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final VariableHistoryItemFilter filter) {
    return and(
        longOperations(VARIABLE_KEY, filter.variableKeyOperations()),
        longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()),
        stringOperations(NAME, filter.nameOperations()),
        longOperations(SCOPE_KEY, filter.scopeKeyOperations()),
        stringOperations(ELEMENT_ID, filter.elementIdOperations()),
        longOperations(ELEMENT_INSTANCE_KEY, filter.elementInstanceKeyOperations()),
        longOperations(VARIABLE_HISTORY_ITEM_KEY, filter.variableHistoryItemKeyOperations()),
        longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()),
        stringOperations(PROCESS_DEFINITION_ID, filter.processDefinitionIdOperations()),
        stringOperations(TENANT_ID, filter.tenantIdOperations()),
        dateTimeOperations(TIMESTAMP, filter.timestampOperations()),
        stringOperations(OPERATION_TYPE, filter.operationTypeOperations()),
        stringOperations(VARIABLE_OPERATION_TYPE, filter.variableOperationTypeOperations()),
        stringOperations(ACTOR_TYPE, filter.actorTypeOperations()),
        stringOperations(ACTOR_ID, filter.actorIdOperations()),
        stringOperations(AGENT_ELEMENT_ID, filter.agentElementIdOperations()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(PROCESS_DEFINITION_ID, authorization.resourceIds());
  }
}
