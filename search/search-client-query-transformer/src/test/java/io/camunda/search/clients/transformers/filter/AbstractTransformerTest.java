/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBase;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;

public class AbstractTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  protected <F extends FilterBase> SearchQuery transformQuery(final F filter) {
    return transformQuery(filter, ResourceAccessChecks.disabled());
  }

  protected <F extends FilterBase> SearchQuery transformQuery(
      final F filter, final ResourceAccessChecks resourceAccessChecks) {
    return ((IndexFilterTransformer) transformers.getFilterTransformer(filter.getClass()))
        .toSearchQuery(filter, resourceAccessChecks);
  }
}
