/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql.resolvers;

import static io.camunda.tasklist.webapp.graphql.TasklistGraphQLContextBuilder.VARIABLE_DATA_LOADER;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.VariableStore.GetVariablesRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.dataloader.DataLoader;

public class TaskVariablesFetcher implements DataFetcher<CompletableFuture<List<VariableDTO>>> {

  @Override
  public CompletableFuture<List<VariableDTO>> get(final DataFetchingEnvironment env)
      throws Exception {
    final DataLoader<GetVariablesRequest, List<VariableDTO>> dataloader =
        env.getDataLoader(VARIABLE_DATA_LOADER);

    return dataloader.load(
        VariableStore.GetVariablesRequest.createFrom(
            TaskDTO.toTaskEntity(env.getSource()),
            env.getSelectionSet().getFields().stream()
                .map(SelectedField::getName)
                .collect(Collectors.toSet())));
  }
}
