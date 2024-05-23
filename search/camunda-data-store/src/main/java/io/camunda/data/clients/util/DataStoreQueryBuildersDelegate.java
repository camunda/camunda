/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.util;

import io.camunda.data.clients.query.DataStoreBoolQuery;
import io.camunda.data.clients.query.DataStoreConstantScoreQuery;
import io.camunda.data.clients.query.DataStoreExistsQuery;
import io.camunda.data.clients.query.DataStoreHasChildQuery;
import io.camunda.data.clients.query.DataStoreIdsQuery;
import io.camunda.data.clients.query.DataStoreMatchAllQuery;
import io.camunda.data.clients.query.DataStoreMatchNoneQuery;
import io.camunda.data.clients.query.DataStoreMatchQuery;
import io.camunda.data.clients.query.DataStorePrefixQuery;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.clients.query.DataStoreRangeQuery;
import io.camunda.data.clients.query.DataStoreTermQuery;
import io.camunda.data.clients.query.DataStoreTermsQuery;
import io.camunda.data.clients.query.DataStoreWildcardQuery;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public interface DataStoreQueryBuildersDelegate {

  DataStoreBoolQuery.Builder bool();

  DataStoreBoolQuery bool(
      final Function<DataStoreBoolQuery.Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn);

  DataStoreConstantScoreQuery.Builder constantScore();

  DataStoreConstantScoreQuery constantScore(
      final Function<
              DataStoreConstantScoreQuery.Builder,
              DataStoreObjectBuilder<DataStoreConstantScoreQuery>>
          fn);

  DataStoreExistsQuery.Builder exists();

  DataStoreExistsQuery exists(
      final Function<DataStoreExistsQuery.Builder, DataStoreObjectBuilder<DataStoreExistsQuery>>
          fn);

  DataStoreHasChildQuery.Builder hasChild();

  DataStoreHasChildQuery hasChild(
      final Function<DataStoreHasChildQuery.Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>>
          fn);

  DataStoreIdsQuery.Builder ids();

  DataStoreIdsQuery ids(
      final Function<DataStoreIdsQuery.Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn);

  DataStoreMatchQuery.Builder match();

  DataStoreMatchQuery match(
      final Function<DataStoreMatchQuery.Builder, DataStoreObjectBuilder<DataStoreMatchQuery>> fn);

  DataStoreMatchAllQuery.Builder matchAll();

  DataStoreMatchAllQuery matchAll(
      final Function<DataStoreMatchAllQuery.Builder, DataStoreObjectBuilder<DataStoreMatchAllQuery>>
          fn);

  DataStoreMatchNoneQuery.Builder matchNone();

  DataStoreMatchNoneQuery matchNone(
      final Function<
              DataStoreMatchNoneQuery.Builder, DataStoreObjectBuilder<DataStoreMatchNoneQuery>>
          fn);

  DataStorePrefixQuery.Builder prefix();

  DataStorePrefixQuery prefix(
      final Function<DataStorePrefixQuery.Builder, DataStoreObjectBuilder<DataStorePrefixQuery>>
          fn);

  DataStoreQuery.Builder query();

  DataStoreQuery query(
      final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn);

  DataStoreRangeQuery.Builder range();

  DataStoreRangeQuery range(
      final Function<DataStoreRangeQuery.Builder, DataStoreObjectBuilder<DataStoreRangeQuery>> fn);

  DataStoreTermQuery.Builder term();

  DataStoreTermQuery term(
      final Function<DataStoreTermQuery.Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn);

  DataStoreTermsQuery.Builder terms();

  DataStoreTermsQuery terms(
      final Function<DataStoreTermsQuery.Builder, DataStoreObjectBuilder<DataStoreTermsQuery>> fn);

  DataStoreWildcardQuery.Builder wildcard();

  DataStoreWildcardQuery wildcard(
      final Function<DataStoreWildcardQuery.Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>>
          fn);
}
