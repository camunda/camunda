/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.service.VariableService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableQueryResolver implements GraphQLQueryResolver {

  @Autowired private VariableService variableService;

  public List<VariableDTO> variables(
      String taskId, List<String> variableNames, DataFetchingEnvironment dfe) {
    return variableService.getVariables(taskId, variableNames, getFieldNames(dfe));
  }

  /**
   * Variable id here can be either "scopeId-varName" for runtime variables or "taskId-varName" for
   * completed task variables
   *
   * @param variableId
   * @return
   */
  public VariableDTO variable(String variableId, DataFetchingEnvironment dfe) {
    return variableService.getVariable(variableId, getFieldNames(dfe));
  }

  private Set<String> getFieldNames(DataFetchingEnvironment dataFetchingEnvironment) {
    return dataFetchingEnvironment.getSelectionSet().getFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toSet());
  }
}
