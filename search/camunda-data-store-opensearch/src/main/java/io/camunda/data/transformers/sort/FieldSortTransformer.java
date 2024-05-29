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

  public FieldSortTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public FieldSort apply(final DataStoreFieldSort value) {
    final var field = value.field();
    final var order = toSortOrder(value);

    return SortOptionsBuilders.field().field(field).order(order).build();
  }

  private SortOrder toSortOrder(final DataStoreFieldSort value) {
    if (value != null) {
      if (value.asc()) {
        return SortOrder.Asc;
      } else if (value.desc()) {
        return SortOrder.Desc;
      }
    }
    return null;
  }
}
