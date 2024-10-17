/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.UserTaskQuery.Builder;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class UserTaskServices
    extends SearchQueryService<UserTaskServices, UserTaskQuery, UserTaskEntity> {

  private final UserTaskSearchClient userTaskSearchClient;

  public UserTaskServices(
      final BrokerClient brokerClient,
      final UserTaskSearchClient userTaskSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.userTaskSearchClient = userTaskSearchClient;
  }

  @Override
  public UserTaskServices withAuthentication(final Authentication authentication) {
    return new UserTaskServices(brokerClient, userTaskSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    return userTaskSearchClient.searchUserTasks(query, authentication);
  }

  public SearchQueryResult<UserTaskEntity> search(
      final Function<Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return search(SearchQueryBuilders.userTaskSearchQuery(fn));
  }

  public CompletableFuture<UserTaskRecord> assignUserTask(
      final long userTaskKey,
      final String assignee,
      final String action,
      final boolean allowOverride) {
    return sendBrokerRequest(
        new BrokerUserTaskAssignmentRequest(
            userTaskKey,
            assignee,
            action,
            allowOverride ? UserTaskIntent.ASSIGN : UserTaskIntent.CLAIM));
  }

  public CompletableFuture<UserTaskRecord> completeUserTask(
      final long userTaskKey, final Map<String, Object> variables, final String action) {
    return sendBrokerRequest(
        new BrokerUserTaskCompletionRequest(userTaskKey, getDocumentOrEmpty(variables), action));
  }

  public CompletableFuture<UserTaskRecord> unassignUserTask(
      final long userTaskKey, final String action) {
    return sendBrokerRequest(
        new BrokerUserTaskAssignmentRequest(userTaskKey, "", action, UserTaskIntent.ASSIGN));
  }

  public CompletableFuture<UserTaskRecord> updateUserTask(
      final long userTaskKey, final UserTaskRecord changeset, final String action) {
    return sendBrokerRequest(new BrokerUserTaskUpdateRequest(userTaskKey, changeset, action));
  }

  public UserTaskEntity getByKey(final Long key) {
    final SearchQueryResult<UserTaskEntity> result =
        search(SearchQueryBuilders.userTaskSearchQuery().filter(f -> f.userTaskKeys(key)).build());
    if (result.total() < 1) {
      throw new NotFoundException(String.format("User Task with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found User Task with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }
}
