/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.List;
import java.util.function.Function;

public interface DataStoreIdsQuery extends DataStoreQueryVariant {

  static DataStoreIdsQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn) {
    return DataStoreQueryBuilders.ids(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreIdsQuery> {

    Builder values(final List<String> list);

    Builder values(final String value, final String... values);
  }
}
