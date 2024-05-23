/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.types.DataStoreSortOptions;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;
import java.util.function.Function;

public interface DataStoreSearchRequest {

  Object get();

  public static DataStoreSearchRequest of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreSearchRequest>> fn) {
    return DataStoreRequestBuilders.searchRequest(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreSearchRequest> {

    Builder index(final List<String> indices);

    Builder index(final String index, final String... indices);

    Builder query(final DataStoreQuery query);

    Builder query(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn);

    Builder sort(final List<DataStoreSortOptions> list);

    Builder sort(final DataStoreSortOptions sort, final DataStoreSortOptions... options);

    Builder sort(
        final Function<DataStoreSortOptions.Builder, DataStoreObjectBuilder<DataStoreSortOptions>>
            fn);

    Builder searchAfter(final Object[] searchAfter);

    Builder size(final Integer size);

    Builder from(final Integer from);
  }
}
