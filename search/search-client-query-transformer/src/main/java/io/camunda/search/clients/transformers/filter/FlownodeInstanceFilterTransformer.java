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
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.*;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class FlownodeInstanceFilterTransformer
    extends IndexFilterTransformer<FlowNodeInstanceFilter> {

  public FlownodeInstanceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final FlowNodeInstanceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(longTerms(KEY, filter.flowNodeInstanceKeys())).ifPresent(queries::add);
    ofNullable(longTerms(PROCESS_INSTANCE_KEY, filter.processInstanceKeys()))
        .ifPresent(queries::add);
    ofNullable(longTerms(PROCESS_DEFINITION_KEY, filter.processDefinitionKeys()))
        .ifPresent(queries::add);
    ofNullable(stringTerms(BPMN_PROCESS_ID, filter.processDefinitionIds())).ifPresent(queries::add);
    ofNullable(getTypeQuery(filter.types())).ifPresent(queries::add);
    queries.addAll(stringOperations(STATE, filter.stateOperations()));
    ofNullable(stringTerms(FLOW_NODE_ID, filter.flowNodeIds())).ifPresent(queries::add);
    ofNullable(stringTerms(FLOW_NODE_NAME, filter.flowNodeNames())).ifPresent(queries::add);
    ofNullable(longTerms(INCIDENT_KEY, filter.incidentKeys())).ifPresent(queries::add);
    ofNullable(stringTerms(TREE_PATH, filter.treePaths())).ifPresent(queries::add);
    ofNullable(filter.hasIncident()).ifPresent(f -> queries.add(term(INCIDENT, f)));
    ofNullable(stringTerms(TENANT_ID, filter.tenantIds())).ifPresent(queries::add);
    queries.addAll(dateTimeOperations(START_DATE, filter.startDateOperations()));
    queries.addAll(dateTimeOperations(END_DATE, filter.endDateOperations()));
    return and(queries);
  }

  private SearchQuery getTypeQuery(final List<FlowNodeType> types) {
    return stringTerms(TYPE, types != null ? types.stream().map(Enum::name).toList() : null);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
