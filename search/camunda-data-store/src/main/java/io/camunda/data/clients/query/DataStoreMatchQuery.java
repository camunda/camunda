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

public interface DataStoreMatchQuery extends DataStoreQueryVariant {

  static DataStoreMatchQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreMatchQuery>> fn) {
    return DataStoreQueryBuilders.match(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreMatchQuery> {

    Builder field(final String value);

    Builder query(final String query);

    Builder operator(final String value);
  }
}
