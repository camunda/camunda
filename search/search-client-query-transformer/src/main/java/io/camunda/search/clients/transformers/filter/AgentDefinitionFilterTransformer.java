/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.AGENT_TYPE;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.TENANT_ID;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class AgentDefinitionFilterTransformer
    extends IndexFilterTransformer<AgentDefinitionFilter> {

  public AgentDefinitionFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final AgentDefinitionFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(longOperations(KEY, filter.agentDefinitionKeyOperations()));
    queries.addAll(stringOperations(AGENT_TYPE, filter.agentTypeOperations()));
    queries.addAll(stringOperations(NAME, filter.nameOperations()));
    queries.addAll(stringOperations(ELEMENT_ID, filter.elementIdOperations()));
    queries.addAll(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()));
    queries.addAll(longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()));
    queries.addAll(
        intOperations(PROCESS_DEFINITION_VERSION, filter.processDefinitionVersionOperations()));
    queries.addAll(
        stringOperations(
            PROCESS_DEFINITION_VERSION_TAG, filter.processDefinitionVersionTagOperations()));
    queries.addAll(stringOperations(TENANT_ID, filter.tenantIdOperations()));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
