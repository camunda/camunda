/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.match;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.CREATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG_HASH;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_INSTANCE_KEY;

import io.camunda.search.clients.query.SearchMatchQuery.SearchMatchQueryOperator;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class IncidentFilterTransformer extends IndexFilterTransformer<IncidentFilter> {

  private final ServiceTransformers transformers;

  public IncidentFilterTransformer(
      final ServiceTransformers transformers, final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final IncidentFilter filter) {
    final var keyQuery = getKeyQuery(filter.incidentKeys());
    final var processDefinitionKeyQuery =
        getProcessDefinitionKeyQuery(filter.processDefinitionKeys());
    final var processDefinitionIdQuery = getProcessDefinitionIds(filter.processDefinitionIds());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());
    final var errorTypeQuery = getErrorTypeQuery(filter.errorTypes());
    final var errorMessageQuery = getErrorMessageQuery(filter.errorMessages());
    final var flowNodeIdQuery = getFlowNodeIdQuery(filter.flowNodeIds());
    final var flowNodeInstanceKeyQuery = getFlowNodeInstanceKeyQuery(filter.flowNodeInstanceKeys());
    final var creationTimeQuery = getCreationTimeQuery(filter.creationTime());
    final var stateQuery = getStateQuery(filter.states());
    final var jobKeyQuery = getJobKeyQuery(filter.jobKeys());
    final var tenantIdQuery = getTenantIdQuery(filter.tenantIds());
    final var incidentErrorHashCodeQuery =
        getIncidentErrorHashCodeQuery(filter.incidentErrorHashCodes());

    return and(
        keyQuery,
        processDefinitionKeyQuery,
        processDefinitionIdQuery,
        processInstanceKeyQuery,
        errorTypeQuery,
        errorMessageQuery,
        flowNodeIdQuery,
        flowNodeInstanceKeyQuery,
        creationTimeQuery,
        stateQuery,
        jobKeyQuery,
        tenantIdQuery,
        incidentErrorHashCodeQuery);
  }

  private SearchQuery getTenantIdQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }

  private SearchQuery getJobKeyQuery(final List<Long> jobKeys) {
    return longTerms(JOB_KEY, jobKeys);
  }

  private SearchQuery getStateQuery(final List<IncidentState> states) {
    return stringTerms("state", states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getCreationTimeQuery(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = transformers.getFilterTransformer(DateValueFilter.class);
      return transformer.apply(new DateFieldFilter(CREATION_TIME, filter));
    }
    return null;
  }

  private SearchQuery getProcessDefinitionIds(final List<String> bpmnProcessIds) {
    return stringTerms(BPMN_PROCESS_ID, bpmnProcessIds);
  }

  private SearchQuery getFlowNodeInstanceKeyQuery(final List<Long> flowNodeInstanceKeys) {
    return longTerms(FLOW_NODE_INSTANCE_KEY, flowNodeInstanceKeys);
  }

  private SearchQuery getFlowNodeIdQuery(final List<String> flowNodeIds) {
    return stringTerms(FLOW_NODE_ID, flowNodeIds);
  }

  private SearchQuery getErrorTypeQuery(final List<ErrorType> errorTypes) {
    return stringTerms(
        ERROR_TYPE, errorTypes != null ? errorTypes.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKeys) {
    return longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<Long> processDefinitionKeys) {
    return longTerms(PROCESS_DEFINITION_KEY, processDefinitionKeys);
  }

  private SearchQuery getErrorMessageQuery(final List<String> errorMessages) {
    return and(
        errorMessages.stream()
            .map(e -> match(ERROR_MSG, e, SearchMatchQueryOperator.AND))
            .toList());
  }

  private SearchQuery getKeyQuery(final List<Long> keys) {
    return longTerms(KEY, keys);
  }

  private SearchQuery getIncidentErrorHashCodeQuery(final List<Integer> incidentErrorHashCodes) {
    return intTerms(ERROR_MSG_HASH, incidentErrorHashCodes);
  }
}
