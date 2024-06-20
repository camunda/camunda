/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.ProcessDefinitionFilter;
import io.camunda.service.transformers.ServiceTransformers;
import java.util.Arrays;
import java.util.List;

public final class ProcessDefinitionFilterTransformer
    implements FilterTransformer<ProcessDefinitionFilter> {

  private final ServiceTransformers transformers;

  public ProcessDefinitionFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessDefinitionFilter filter) {
    final var keysQuery = longTerms("key", filter.keys());
    final var bpmnProcessIdsQuery = stringTerms("bpmnProcessId", filter.bpmnProcessIds());
    final var namesQuery = stringTerms("name", filter.names());
    final var versionsQuery = intTerms("version", filter.versions());
    final var formKeysQuery = longTerms("formKey", filter.formKeys());

    return and(keysQuery, bpmnProcessIdsQuery, namesQuery, versionsQuery, formKeysQuery);
  }

  @Override
  public List<String> toIndices(final ProcessDefinitionFilter filter) {
    // We use tasklist process index due to field formKey
    return Arrays.asList("tasklist-process-8.4.0_");
  }
}
