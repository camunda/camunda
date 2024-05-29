/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.sort;

import io.camunda.data.clients.sort.DataStoreFieldSort;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptionsBuilders;
import org.opensearch.client.opensearch._types.SortOrder;

public final class FieldSortTransformer
    extends OpensearchTransformer<DataStoreFieldSort, FieldSort> {

  public FieldSortTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public FieldSort apply(final DataStoreFieldSort value) {
    return SortOptionsBuilders.field().field(value.field()).order(toSortOrder(value)).build();
  }

  private SortOrder toSortOrder(final DataStoreFieldSort value) {
    if (value.asc()) {
      return SortOrder.Asc;
    } else if (value.desc()) {
      return SortOrder.Desc;
    } else {
      return null;
    }
  }
}
