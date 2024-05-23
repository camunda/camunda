/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public interface DataStoreWildcardQuery extends DataStoreQueryVariant {

  static DataStoreWildcardQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>> fn) {
    return DataStoreQueryBuilders.wildcard(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreWildcardQuery> {

    Builder field(final String field);

    Builder value(final String value);
  }
}
