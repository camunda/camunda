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
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_KEY;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ProcessInstanceStatisticsFilterTransformer
    extends IndexFilterTransformer<ProcessInstanceStatisticsFilter> {

  public ProcessInstanceStatisticsFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessInstanceStatisticsFilter filter) {
    return and(
        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        term(PROCESS_INSTANCE_KEY, filter.processInstanceKey()));
  }
}
