/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.ELEMENT_TYPE;
import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.ROOT_PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.WAIT_STATE_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class WaitStateFilterTransformer
    extends IndexFilterTransformer<ElementInstanceWaitStateFilter> {

  public WaitStateFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }

  @Override
  public SearchQuery toSearchQuery(final ElementInstanceWaitStateFilter filter) {
    return and(toSearchQueryFields(filter));
  }

  private List<SearchQuery> toSearchQueryFields(final ElementInstanceWaitStateFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(longOperations(ELEMENT_INSTANCE_KEY, filter.elementInstanceKeyOperations()));
    queries.addAll(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()));
    queries.addAll(
        longOperations(ROOT_PROCESS_INSTANCE_KEY, filter.rootProcessInstanceKeyOperations()));
    queries.addAll(stringOperations(ELEMENT_ID, filter.elementIdOperations()));
    queries.addAll(stringOperations(ELEMENT_TYPE, filter.elementTypeOperations()));
    queries.addAll(stringOperations(WAIT_STATE_TYPE, filter.waitStateTypeOperations()));
    return queries;
  }
}
