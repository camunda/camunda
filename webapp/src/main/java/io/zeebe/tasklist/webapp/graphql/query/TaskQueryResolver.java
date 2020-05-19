/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import io.zeebe.tasklist.webapp.graphql.entity.Task;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class TaskQueryResolver implements GraphQLQueryResolver {

  public List<Task> tasks() {
    final var task = new Task();
    task.setKey("123");
    return List.of(task);
  }
}
