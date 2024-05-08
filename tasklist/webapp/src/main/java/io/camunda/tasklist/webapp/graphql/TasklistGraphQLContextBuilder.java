/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql;

import graphql.kickstart.execution.context.DefaultGraphQLContext;
import graphql.kickstart.execution.context.GraphQLKickstartContext;
import graphql.kickstart.servlet.context.GraphQLServletContextBuilder;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.service.VariableService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TasklistGraphQLContextBuilder implements GraphQLServletContextBuilder {

  public static final String USER_DATA_LOADER = "userDataLoader";
  public static final String VARIABLE_DATA_LOADER = "variableDataLoader";

  @Autowired private UserReader userReader;

  @Autowired private VariableService variableService;

  @Override
  public GraphQLKickstartContext build(HttpServletRequest req, HttpServletResponse response) {
    return GraphQLKickstartContext.of(
        buildDataLoaderRegistry(),
        Map.of(
            HttpServletRequest.class, req,
            HttpServletResponse.class, response));
  }

  @Override
  public GraphQLKickstartContext build(Session session, HandshakeRequest request) {
    return GraphQLKickstartContext.of(
        buildDataLoaderRegistry(),
        Map.of(
            Session.class, session,
            HandshakeRequest.class, request));
  }

  @Override
  public GraphQLKickstartContext build() {
    return new DefaultGraphQLContext(buildDataLoaderRegistry());
  }

  private DataLoaderRegistry buildDataLoaderRegistry() {
    return new DataLoaderRegistry()
        .register(
            USER_DATA_LOADER,
            DataLoaderFactory.<String, UserDTO>newDataLoader(
                usernames ->
                    CompletableFuture.supplyAsync(() -> userReader.getUsersByUsernames(usernames))))
        .register(
            VARIABLE_DATA_LOADER,
            DataLoaderFactory.<VariableStore.GetVariablesRequest, List<VariableDTO>>newDataLoader(
                req -> CompletableFuture.supplyAsync(() -> variableService.getVariables(req))));
  }
}
