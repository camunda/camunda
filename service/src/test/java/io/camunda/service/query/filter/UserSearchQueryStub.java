/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.service.util.StubbedCamundaSearchClient.RequestStub;
import java.util.List;

public class UserSearchQueryStub implements RequestStub<UserEntity> {
  @Override
  public SearchQueryResponse<UserEntity> handle(final SearchQueryRequest request) throws Exception {

    final List<SearchQueryHit<UserEntity>> hits =
        List.of(
            new SearchQueryHit.Builder<UserEntity>()
                .id("1")
                .source(
                    new UserEntity(
                        new UserEntity.User("username1", "name1", "email1", "password1")))
                .build(),
            new SearchQueryHit.Builder<UserEntity>()
                .id("2")
                .source(
                    new UserEntity(
                        new UserEntity.User("username2", "name2", "email2", "password2")))
                .build());

    final SearchQueryResponse<UserEntity> response =
        SearchQueryResponse.of(
            (f) -> {
              f.totalHits(hits.size()).hits(hits);
              return f;
            });

    return response;
  }

  @Override
  public void registerWith(final StubbedCamundaSearchClient client) {
    client.registerHandler(this);
  }
}
