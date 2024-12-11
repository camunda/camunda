/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch.core.GetRequest;

public final class SearchGetRequestTransformer
    extends OpensearchTransformer<SearchGetRequest, GetRequest> {

  public SearchGetRequestTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public GetRequest apply(final SearchGetRequest value) {
    final var id = value.id();
    final var index = value.index();
    final var routing = value.routing();
    return GetRequest.of(b -> b.id(id).index(index).routing(routing));
  }
}