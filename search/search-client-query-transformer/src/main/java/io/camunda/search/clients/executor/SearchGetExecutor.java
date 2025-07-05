/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.executor;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;

public class SearchGetExecutor<DOC, RES> {

  private final ServiceTransformer<DOC, RES> documentTransformer;
  private final DocumentBasedSearchClient searchClient;
  private final SearchGetAuthorizationApplier<RES> check;

  public SearchGetExecutor(
      final ServiceTransformer<DOC, RES> documentTransformer,
      final DocumentBasedSearchClient searchClient,
      final SearchGetAuthorizationApplier<RES> check) {
    this.documentTransformer = documentTransformer;
    this.searchClient = searchClient;
    this.check = check;
  }

  public RES execute(final SearchGetRequest request, final Class<DOC> documentClass) {
    final SearchGetResponse<DOC> getResponse = searchClient.get(request, documentClass);
    final var document = documentTransformer.apply(getResponse.source());

    if (!check.check(document)) {
      throw new CamundaSearchException("FOO", Reason.FORBIDDEN);
    }

    return document;
  }
}
