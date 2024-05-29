/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.service.query.SearchQueryResult;
import io.camunda.service.query.filter.FilterBuilders;
import io.camunda.zeebe.util.Either;
import org.junit.jupiter.api.Test;

public class CamundaServicesTest {

  private DataStoreSearchRequest actualRequest;

  @Test
  public void shouldReceiveExpectedRequest() {
    // given
    final DataStoreSearchRequest expected =
        DataStoreSearchRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    final var dataStoreClient =
        new DataStoreClient() {
          @Override
          public <T> Either<Exception, DataStoreSearchResponse<T>> search(
              final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
            actualRequest = searchRequest;
            return Either.right(DataStoreSearchResponse.of(b -> b));
          }
        };
    final var camundaServices = new CamundaServices(dataStoreClient);

    // when
    final var filter =
        FilterBuilders.processInstance((f) -> f.processInstanceKeys(4503599627370497L));
    final var response =
        camundaServices
            .processInstanceServices()
            .search(
                (b) ->
                    b.filter(filter)
                        .sort((s) -> s.field("endDate").asc())
                        .page((p) -> p.from(0).size(20)));

    // then
    assertThat(actualRequest).isEqualTo(expected);

    // verify response -

  }

  @Test
  public void shouldReceiveExpectedResponse() {
    // given
    final SearchQueryResult expectedResponse =
        SearchQueryResult.from(DataStoreSearchResponse.of(b -> b));

    final var dataStoreClient =
        new DataStoreClient() {
          @Override
          public <T> Either<Exception, DataStoreSearchResponse<T>> search(
              final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
            actualRequest = searchRequest;
            return Either.right(DataStoreSearchResponse.of(b -> b));
          }
        };
    final var camundaServices = new CamundaServices(dataStoreClient);

    // when
    final var filter =
        FilterBuilders.processInstance((f) -> f.processInstanceKeys(4503599627370497L));
    final var actualResponse =
        camundaServices
            .processInstanceServices()
            .search(
                (b) ->
                    b.filter(filter)
                        .sort((s) -> s.field("endDate").asc())
                        .page((p) -> p.from(0).size(20)));

    // then
    assertThat(actualResponse).isEqualTo(expectedResponse);

    // verify response -

  }

  @Test
  public void shouldThrowError() {
    // given
    final DataStoreSearchRequest expected =
        DataStoreSearchRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    final var dataStoreClient =
        new DataStoreClient() {
          @Override
          public <T> Either<Exception, DataStoreSearchResponse<T>> search(
              final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
            actualRequest = searchRequest;
            return Either.left(new RuntimeException());
          }
        };
    final var camundaServices = new CamundaServices(dataStoreClient);

    // when
    final var filter =
        FilterBuilders.processInstance((f) -> f.processInstanceKeys(4503599627370497L));
    final var errorResponse =
        camundaServices
            .processInstanceServices()
            .search(
                (b) ->
                    b.filter(filter)
                        .sort((s) -> s.field("endDate").asc())
                        .page((p) -> p.from(0).size(20)));

    // then - response is error
    assertThat(errorResponse); // is error
  }
}
