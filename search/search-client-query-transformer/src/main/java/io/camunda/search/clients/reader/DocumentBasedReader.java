/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public abstract class DocumentBasedReader {

  protected final DocumentBasedSearchClient searchClient;
  protected final ServiceTransformers transformers;
  protected final IndexDescriptor indexDescriptor;
  protected final SearchClientBasedQueryExecutor searchExecutor;

  public DocumentBasedReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.indexDescriptor = indexDescriptor;
    searchExecutor = new SearchClientBasedQueryExecutor(searchClient, transformers);
  }

  protected SearchClientBasedQueryExecutor getSearchExecutor() {
    return searchExecutor;
  }
}
