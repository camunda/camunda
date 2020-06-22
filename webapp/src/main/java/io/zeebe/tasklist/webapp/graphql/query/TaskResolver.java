/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import graphql.kickstart.execution.context.GraphQLContext;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.zeebe.tasklist.webapp.es.reader.UserReader;
import io.zeebe.tasklist.webapp.graphql.entity.TaskDTO;
import io.zeebe.tasklist.webapp.graphql.entity.UserDTO;
import static io.zeebe.tasklist.webapp.graphql.TasklistGraphQLContextBuilder.USER_DATA_LOADER;

@Component
public class TaskResolver implements GraphQLResolver<TaskDTO> {

  @Autowired
  private UserReader userReader;

  public CompletableFuture<UserDTO> getAssignee(TaskDTO task, DataFetchingEnvironment dfe) {
    if (task.getAssigneeUsername() == null) {
      return null;
    }
    final DataLoader<String, UserDTO> dataloader = ((GraphQLContext) dfe.getContext())
        .getDataLoaderRegistry().get()
        .getDataLoader(USER_DATA_LOADER);

    return dataloader.load(task.getAssigneeUsername());
  }

}
