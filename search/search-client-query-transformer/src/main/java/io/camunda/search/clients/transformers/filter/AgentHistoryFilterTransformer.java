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
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.AGENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.COMMIT_STATUS;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.ITERATION;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.PRODUCED_AT;
import static io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate.ROLE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class AgentHistoryFilterTransformer
    extends IndexFilterTransformer<AgentInstanceHistoryFilter> {

  public AgentHistoryFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final AgentInstanceHistoryFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(longOperations(AGENT_INSTANCE_KEY, filter.agentInstanceKeyOperations()));
    queries.addAll(longOperations(KEY, filter.historyItemKeyOperations()));
    queries.addAll(stringOperations(ROLE, filter.roleOperations()));
    queries.addAll(longOperations(ELEMENT_INSTANCE_KEY, filter.elementInstanceKeyOperations()));
    queries.addAll(longOperations(JOB_KEY, filter.jobKeyOperations()));
    queries.addAll(intOperations(ITERATION, filter.iterationOperations()));
    queries.addAll(stringOperations(COMMIT_STATUS, filter.commitStatusOperations()));
    queries.addAll(dateTimeOperations(PRODUCED_AT, filter.producedAtOperations()));
    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }
}
