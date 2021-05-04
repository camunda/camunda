/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import io.camunda.tasklist.webapp.es.VariableReaderWriter;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableQueryResolver implements GraphQLQueryResolver {

  @Autowired private VariableReaderWriter variableReaderWriter;

  public List<VariableDTO> variables(String taskId, List<String> variableNames) {
    return variableReaderWriter.getVariables(taskId, variableNames);
  }
}
