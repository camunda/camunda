/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.query;

import static io.camunda.tasklist.util.CollectionUtil.countNonNullObjects;
import static io.camunda.tasklist.util.CollectionUtil.map;

import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class TaskQueryResolver implements GraphQLQueryResolver {

  @Autowired private TaskReaderWriter taskReaderWriter;

  public List<TaskDTO> tasks(TaskQueryDTO query, DataFetchingEnvironment dataFetchingEnvironment) {
    if (countNonNullObjects(
            query.getSearchAfter(), query.getSearchAfterOrEqual(),
            query.getSearchBefore(), query.getSearchBeforeOrEqual())
        > 1) {
      throw new InvalidRequestException(
          "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
    }
    return taskReaderWriter.getTasks(query, getFieldNames(dataFetchingEnvironment));
  }

  public TaskDTO task(String id, DataFetchingEnvironment dataFetchingEnvironment) {
    final List<SelectedField> fields = dataFetchingEnvironment.getSelectionSet().getFields();
    return taskReaderWriter.getTaskDTO(id, getFieldNames(dataFetchingEnvironment));
  }

  private List<String> getFieldNames(DataFetchingEnvironment dataFetchingEnvironment) {
    return map(dataFetchingEnvironment.getSelectionSet().getFields(), SelectedField::getName);
  }
}
