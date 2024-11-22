/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import io.camunda.search.filter.FilterBase;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public abstract class IndexFilterTransformer<T extends FilterBase> implements FilterTransformer<T> {

  private final IndexDescriptor indexDescriptor;

  public IndexFilterTransformer(final IndexDescriptor indexDescriptor) {
    this.indexDescriptor = indexDescriptor;
  }

  @Override
  public IndexDescriptor getIndex() {
    return indexDescriptor;
  }
}
