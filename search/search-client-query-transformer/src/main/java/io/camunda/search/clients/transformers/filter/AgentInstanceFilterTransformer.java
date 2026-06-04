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
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.ROOT_PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.COMPLETION_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.CREATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.ELEMENT_INSTANCE_KEYS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.LAST_UPDATED_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.STATUS;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.VERSION_TAG;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class AgentInstanceFilterTransformer extends IndexFilterTransformer<AgentInstanceFilter> {

  public AgentInstanceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final AgentInstanceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(longOperations(KEY, filter.agentInstanceKeyOperations()));
    queries.addAll(longOperations(ELEMENT_INSTANCE_KEYS, filter.elementInstanceKeyOperations()));
    queries.addAll(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()));
    queries.addAll(
        longOperations(ROOT_PROCESS_INSTANCE_KEY, filter.rootProcessInstanceKeyOperations()));
    queries.addAll(longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()));
    queries.addAll(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()));
    queries.addAll(
        intOperations(PROCESS_DEFINITION_VERSION, filter.processDefinitionVersionOperations()));
    queries.addAll(stringOperations(VERSION_TAG, filter.versionTagOperations()));
    queries.addAll(stringOperations(ELEMENT_ID, filter.elementIdOperations()));
    queries.addAll(stringOperations(STATUS, filter.statusOperations()));
    queries.addAll(stringOperations(TENANT_ID, filter.tenantIdOperations()));
    queries.addAll(dateTimeOperations(CREATION_DATE, filter.creationDateOperations()));
    queries.addAll(dateTimeOperations(LAST_UPDATED_DATE, filter.lastUpdatedDateOperations()));
    queries.addAll(dateTimeOperations(COMPLETION_DATE, filter.completionDateOperations()));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
