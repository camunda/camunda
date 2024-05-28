/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.service;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchRequest.Builder;
import io.camunda.data.clients.core.DataStoreSearchResponse;
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
import io.camunda.data.clients.util.DataStoreQueryBuildersDelegate;
import io.camunda.data.clients.util.DataStoreRequestBuildersDelegate;
import io.camunda.data.clients.util.DataStoreSortOptionsBuildersDelegate;
import io.camunda.service.auth.Authentication;
import io.camunda.service.query.SearchQuery;
import io.camunda.service.query.filter.ProcessInstanceFilterTest;
import io.camunda.service.query.types.SearchQueryPage;
import io.camunda.service.query.types.SearchQuerySort;
import io.camunda.util.DataStoreObjectBuilder;
import io.camunda.zeebe.util.Either;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class CamundaServicesTest {

  @Test
  public void should() {
    final var dataStoreClient =
        new DataStoreClient() {
          @Override
          public DataStoreQueryBuildersDelegate getBuildersDelegate() {

            return new MyDataStoreQueryBuildersDelegate();
          }

          @Override
          public DataStoreRequestBuildersDelegate getRequestDelegate() {
            return null;
          }

          @Override
          public DataStoreSortOptionsBuildersDelegate getSortOptionsDelegate() {
            return null;
          }

          @Override
          public <T> Either<Exception, DataStoreSearchResponse<T>> search(
              final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {


            searchRequest.

            return null;
          }

          @Override
          public <T> Either<Exception, DataStoreSearchResponse<T>> search(
              final Function<Builder, DataStoreObjectBuilder<DataStoreSearchRequest>> fn,
              final Class<T> documentClass) {
            return null;
          }
        };

    final var camundaServices = new CamundaServices(dataStoreClient);

    // when



    // test builder -> verify values
    final Authentication build = new Authentication.Builder().group("group").user("me")
        .tenant("test").build();

    // test query - verify values
//    build.toSearchQuery()

    //
    final var camundaServiceWithAuth = camundaServices.withAuthentication(build);

    // -> process instance service with auth
    final var processInstanceServices = camundaServiceWithAuth.processInstanceServices();


    // test search
    final var searchQueryBuilder = new SearchQuery.Builder<ProcessInstanceFilterTest>();

    // test paging
    final var searchQueryPage = SearchQueryPage.ofSize(50);


    // test sort -> validate mandatory fields like "field"
    final var searchQuerySort = SearchQuerySort.of(builder -> builder.field("field").asc());


    // test filter - mandatory -
    final ProcessInstanceFilterTest filter = new ProcessInstanceFilterTest.Builder()..build();// all


    // test query
    final SearchQuery<ProcessInstanceFilterTest> query = searchQueryBuilder.page(
            searchQueryPage)
        .sort(searchQuerySort)
        .filter(filter).build();



//    query.toSearchRequest()

    processInstanceServices.search(query);

    searchQueryBuilder.page(SearchQueryPage.of())
    processInstanceServices.search()

  }

  private static class MyDataStoreQueryBuildersDelegate implements DataStoreQueryBuildersDelegate {

    @Override
    public DataStoreBoolQuery.Builder bool() {
      return null;
    }

    @Override
    public DataStoreBoolQuery bool(
        final Function<DataStoreBoolQuery.Builder, DataStoreObjectBuilder<DataStoreBoolQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreConstantScoreQuery.Builder constantScore() {
      return null;
    }

    @Override
    public DataStoreConstantScoreQuery constantScore(
        final Function<DataStoreConstantScoreQuery.Builder, DataStoreObjectBuilder<DataStoreConstantScoreQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreExistsQuery.Builder exists() {
      return null;
    }

    @Override
    public DataStoreExistsQuery exists(
        final Function<DataStoreExistsQuery.Builder, DataStoreObjectBuilder<DataStoreExistsQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreHasChildQuery.Builder hasChild() {
      return null;
    }

    @Override
    public DataStoreHasChildQuery hasChild(
        final Function<DataStoreHasChildQuery.Builder, DataStoreObjectBuilder<DataStoreHasChildQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreIdsQuery.Builder ids() {
      return null;
    }

    @Override
    public DataStoreIdsQuery ids(
        final Function<DataStoreIdsQuery.Builder, DataStoreObjectBuilder<DataStoreIdsQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreMatchQuery.Builder match() {
      return null;
    }

    @Override
    public DataStoreMatchQuery match(
        final Function<DataStoreMatchQuery.Builder, DataStoreObjectBuilder<DataStoreMatchQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreMatchAllQuery.Builder matchAll() {
      return null;
    }

    @Override
    public DataStoreMatchAllQuery matchAll(
        final Function<DataStoreMatchAllQuery.Builder, DataStoreObjectBuilder<DataStoreMatchAllQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreMatchNoneQuery.Builder matchNone() {
      return null;
    }

    @Override
    public DataStoreMatchNoneQuery matchNone(
        final Function<DataStoreMatchNoneQuery.Builder, DataStoreObjectBuilder<DataStoreMatchNoneQuery>> fn) {
      return null;
    }

    @Override
    public DataStorePrefixQuery.Builder prefix() {
      return null;
    }

    @Override
    public DataStorePrefixQuery prefix(
        final Function<DataStorePrefixQuery.Builder, DataStoreObjectBuilder<DataStorePrefixQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreQuery.Builder query() {
      return null;
    }

    @Override
    public DataStoreQuery query(
        final Function<DataStoreQuery.Builder, DataStoreObjectBuilder<DataStoreQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreRangeQuery.Builder range() {
      return null;
    }

    @Override
    public DataStoreRangeQuery range(
        final Function<DataStoreRangeQuery.Builder, DataStoreObjectBuilder<DataStoreRangeQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreTermQuery.Builder term() {
      return null;
    }

    @Override
    public DataStoreTermQuery term(
        final Function<DataStoreTermQuery.Builder, DataStoreObjectBuilder<DataStoreTermQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreTermsQuery.Builder terms() {
      return null;
    }

    @Override
    public DataStoreTermsQuery terms(
        final Function<DataStoreTermsQuery.Builder, DataStoreObjectBuilder<DataStoreTermsQuery>> fn) {
      return null;
    }

    @Override
    public DataStoreWildcardQuery.Builder wildcard() {
      return null;
    }

    @Override
    public DataStoreWildcardQuery wildcard(
        final Function<DataStoreWildcardQuery.Builder, DataStoreObjectBuilder<DataStoreWildcardQuery>> fn) {
      return null;
    }
  }
}
