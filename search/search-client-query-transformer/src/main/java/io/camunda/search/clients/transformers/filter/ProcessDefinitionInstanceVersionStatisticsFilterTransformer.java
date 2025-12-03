/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.Optional;

public class ProcessDefinitionInstanceVersionStatisticsFilterTransformer
    extends IndexFilterTransformer<ProcessDefinitionInstanceVersionStatisticsFilter> {

  public ProcessDefinitionInstanceVersionStatisticsFilterTransformer(
      final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessDefinitionInstanceVersionStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.add(term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    queries.add(term(BPMN_PROCESS_ID, filter.processDefinitionId()));
    queries.add(term(STATE, ProcessInstanceState.ACTIVE.name()));
    Optional.ofNullable(filter.tenantId())
        .ifPresent(tenantId -> queries.add(term(TENANT_ID, tenantId)));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
