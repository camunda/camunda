/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import static io.camunda.tasklist.Metrics.COUNTER_NAME_CLAIMED_TASKS;
import static io.camunda.tasklist.Metrics.COUNTER_NAME_COMPLETED_TASKS;
import static io.camunda.tasklist.Metrics.TAG_KEY_BPMN_PROCESS_ID;
import static io.camunda.tasklist.Metrics.TAG_KEY_FLOW_NODE_ID;
import static io.camunda.tasklist.Metrics.TAG_KEY_ORGANIZATION_ID;
import static io.camunda.tasklist.Metrics.TAG_KEY_USER_ID;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.webapp.es.TaskValidator.CAN_COMPLETE;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TaskValidationException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.es.contract.UsageMetricsContract;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.service.VariableService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class TaskMutationResolver implements GraphQLMutationResolver {

  @Autowired private UserReader userReader;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private TaskReaderWriter taskReaderWriter;

  @Autowired private VariableService variableService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;

  @Autowired private UsageMetricsContract metricsContract;

  @PreAuthorize("hasPermission('write')")
  public TaskDTO completeTask(String taskId, List<VariableInputDTO> variables) {
    final Map<String, Object> variablesMap =
        variables.stream()
            .collect(Collectors.toMap(VariableInputDTO::getName, this::extractTypedValue));
    // validate
    final SearchHit taskSearchHit;
    try {
      taskSearchHit = taskReaderWriter.getTaskRawResponse(taskId);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    final TaskEntity taskBefore =
        fromSearchHit(taskSearchHit.getSourceAsString(), objectMapper, TaskEntity.class);
    try {
      CAN_COMPLETE.validate(taskBefore, getCurrentUser());
    } catch (TaskValidationException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
    // complete
    CompleteJobCommandStep1 completeJobCommand =
        zeebeClient.newCompleteCommand(Long.valueOf(taskId));
    if (variablesMap != null) {
      completeJobCommand = completeJobCommand.variables(variablesMap);
    }
    completeJobCommand.send().join();
    // persist completion and variables
    final TaskEntity completedTask = taskReaderWriter.persistTaskCompletion(taskSearchHit);
    variableService.persistTaskVariables(taskId, variables);
    final TaskDTO task = TaskDTO.createFrom(completedTask, objectMapper);
    updateCompletedMetric(completedTask);
    return task;
  }

  private Object extractTypedValue(VariableInputDTO variable) {
    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  @PreAuthorize("hasPermission('write')")
  public TaskDTO claimTask(String taskId, String assignee) {
    final TaskDTO task = taskReaderWriter.getTaskDTO(taskId, null);
    taskReaderWriter.persistTaskClaim(task, getCurrentUser(), assignee);
    updateClaimedMetric(task);
    return task;
  }

  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @PreAuthorize("hasPermission('write')")
  public TaskDTO unclaimTask(String taskId) {
    final TaskDTO task = taskReaderWriter.getTaskDTO(taskId, null);
    task.setAssignee(null);
    taskReaderWriter.persistTaskUnclaim(task);
    return task;
  }

  private UserDTO getCurrentUser() {
    return userReader.getCurrentUser();
  }

  private void updateClaimedMetric(final TaskDTO task) {
    metrics.recordCounts(COUNTER_NAME_CLAIMED_TASKS, 1, getTaskMetricLabels(task));
  }

  private void updateCompletedMetric(final TaskEntity task) {
    metrics.recordCounts(
        COUNTER_NAME_COMPLETED_TASKS,
        1,
        getTaskMetricLabels(TaskDTO.createFrom(task, objectMapper)));
    metricsContract.registerTaskCompleteEvent(task);
  }

  private String[] getTaskMetricLabels(final TaskDTO task) {
    return new String[] {
      TAG_KEY_BPMN_PROCESS_ID, task.getBpmnProcessId(),
      TAG_KEY_FLOW_NODE_ID, task.getFlowNodeBpmnId(),
      TAG_KEY_USER_ID, userReader.getCurrentUserId(),
      TAG_KEY_ORGANIZATION_ID, userReader.getCurrentOrganizationId()
    };
  }
}
