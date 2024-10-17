/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBase;

public class AbstractTransformerTest {

  private final ServiceTransformers transformers = ServiceTransformers.newInstance();

  protected <F extends FilterBase> SearchQuery transformQuery(final F filter) {
    return transformers.getFilterTransformer(filter.getClass()).apply(filter);
  }
}
