/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql;

import graphql.kickstart.execution.context.DefaultGraphQLContext;
import graphql.kickstart.execution.context.GraphQLKickstartContext;
import graphql.kickstart.servlet.context.GraphQLServletContextBuilder;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.service.VariableService;
import io.camunda.tasklist.webapp.service.VariableService.GetVariablesRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
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
            DataLoaderFactory.<GetVariablesRequest, List<VariableDTO>>newDataLoader(
                req -> CompletableFuture.supplyAsync(() -> variableService.getVariables(req))));
  }
}
