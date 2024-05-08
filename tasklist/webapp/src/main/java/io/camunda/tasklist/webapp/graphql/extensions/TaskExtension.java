/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql.extensions;

import static io.camunda.tasklist.util.SpringContextHolder.getBean;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import graphql.schema.DataFetchingEnvironment;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.mapper.TaskMapper;

@GraphQLTypeExtension(TaskDTO.class)
public class TaskExtension {

  @GraphQLField
  @GraphQLNonNull
  public static String processName(final DataFetchingEnvironment env) {
    return getBean(TaskMapper.class).getProcessName(env.getSource());
  }

  @GraphQLField
  @GraphQLNonNull
  public static String name(final DataFetchingEnvironment env) {
    return getBean(TaskMapper.class).getName(env.getSource());
  }

  @GraphQLField
  public static String taskDefinitionId(final DataFetchingEnvironment env) {
    return ((TaskDTO) env.getSource()).getFlowNodeBpmnId();
  }
}
