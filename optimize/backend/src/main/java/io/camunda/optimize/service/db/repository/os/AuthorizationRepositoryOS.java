/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.AUTHORIZATION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;

import io.camunda.optimize.dto.optimize.access.AuthorizationDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.AuthorizationRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class AuthorizationRepositoryOS implements AuthorizationRepository {

  private final OptimizeOpenSearchClient osClient;

  public AuthorizationRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Optional<AuthorizationDto> get(final String id) {
    final GetResponse<Map> response =
        osClient.get(
            new GetRequest.Builder().index(AUTHORIZATION_INDEX_NAME).id(id),
            Map.class,
            "Could not read authorization " + id);
    return response.found()
        ? Optional.ofNullable(response.source()).map(AuthorizationDto::fromDocument)
        : Optional.empty();
  }

  @Override
  public List<AuthorizationDto> getAll() {
    return osClient
        .scrollValues(
            new SearchRequest.Builder()
                .index(AUTHORIZATION_INDEX_NAME)
                .query(matchAll())
                .size(LIST_FETCH_LIMIT),
            Map.class)
        .stream()
        .map(document -> AuthorizationDto.fromDocument((Map<String, Object>) document))
        .toList();
  }

  @Override
  public void upsert(final AuthorizationDto authorization) {
    osClient.index(
        new IndexRequest.Builder<Map<String, Object>>()
            .index(AUTHORIZATION_INDEX_NAME)
            .id(authorization.id())
            .refresh(Refresh.True)
            .document(authorization.toDocument()));
  }

  @Override
  public void delete(final String id) {
    osClient.delete(
        new DeleteRequest.Builder().index(AUTHORIZATION_INDEX_NAME).id(id).refresh(Refresh.True),
        "Could not delete authorization " + id);
  }
}
