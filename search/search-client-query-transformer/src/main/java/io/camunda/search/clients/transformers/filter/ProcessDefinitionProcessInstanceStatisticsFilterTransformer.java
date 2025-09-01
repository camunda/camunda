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
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION_TAG;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ProcessDefinitionProcessInstanceStatisticsFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import java.util.ArrayList;

public class ProcessDefinitionProcessInstanceStatisticsFilterTransformer
    extends IndexFilterTransformer<ProcessDefinitionProcessInstanceStatisticsFilter> {

  public ProcessDefinitionProcessInstanceStatisticsFilterTransformer(
      final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessDefinitionProcessInstanceStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()));
    queries.addAll(longOperations(PROCESS_KEY, filter.processDefinitionKeyOperations()));
    queries.addAll(intOperations(PROCESS_VERSION, filter.processDefinitionVersionOperations()));
    queries.addAll(
        stringOperations(PROCESS_VERSION_TAG, filter.processDefinitionVersionTagOperations()));
    queries.addAll(stringOperations(PROCESS_NAME, filter.processDefinitionNameOperations()));
    queries.add(term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    queries.add(term(STATE, ProcessInstanceState.ACTIVE.toString()));
    return and(queries);
  }
}
