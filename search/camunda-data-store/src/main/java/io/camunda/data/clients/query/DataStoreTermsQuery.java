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

public interface DataStoreTermsQuery extends DataStoreQueryVariant {

  static DataStoreTermsQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreTermsQuery>> fn) {
    return DataStoreQueryBuilders.terms(fn);
  }

  public interface Builder extends DataStoreObjectBuilder<DataStoreTermsQuery> {

    Builder field(final String value);

    Builder stringTerms(final List<String> values);

    Builder stringTerms(final String value, final String... values);

    Builder intTerms(final List<Integer> values);

    Builder intTerms(final Integer value, final Integer... values);

    Builder longTerms(final List<Long> values);

    Builder longTerms(final Long value, final Long... values);
  }
}
