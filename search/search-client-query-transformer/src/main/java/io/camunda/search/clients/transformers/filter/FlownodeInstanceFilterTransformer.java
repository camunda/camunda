/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.INCIDENT_KEY;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class FlownodeInstanceFilterTransformer
    extends IndexFilterTransformer<FlowNodeInstanceFilter> {

  public FlownodeInstanceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final FlowNodeInstanceFilter filter) {
    return and(
        longTerms(KEY, filter.flowNodeInstanceKeys()),
        longTerms(PROCESS_INSTANCE_KEY, filter.processInstanceKeys()),
        longTerms(PROCESS_DEFINITION_KEY, filter.processDefinitionKeys()),
        stringTerms(BPMN_PROCESS_ID, filter.processDefinitionIds()),
        getStateQuery(filter.states()),
        getTypeQuery(filter.types()),
        stringTerms(FLOW_NODE_ID, filter.flowNodeIds()),
        stringTerms(TREE_PATH, filter.treePaths()),
        filter.hasIncident() != null ? term(INCIDENT, filter.hasIncident()) : null,
        longTerms(INCIDENT_KEY, filter.incidentKeys()),
        stringTerms(TENANT_ID, filter.tenantIds()));
  }

  private SearchQuery getStateQuery(final List<FlowNodeState> states) {
    return stringTerms(STATE, states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getTypeQuery(final List<FlowNodeType> types) {
    return stringTerms(TYPE, types != null ? types.stream().map(Enum::name).toList() : null);
  }
}
