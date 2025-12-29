/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG_HASH;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.STATE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.IncidentProcessInstanceStatisticsByDefinitionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

/** Transformer for the narrow incident stats-by-definition filter. */
public class IncidentProcessInstanceStatisticsByDefinitionFilterTransformer
    extends IndexFilterTransformer<IncidentProcessInstanceStatisticsByDefinitionFilter> {

  public IncidentProcessInstanceStatisticsByDefinitionFilterTransformer(
      final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final IncidentProcessInstanceStatisticsByDefinitionFilter filter) {
    return and(
        term(STATE, filter.state()),
        term(ERROR_MSG_HASH, filter.errorHashCode()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
