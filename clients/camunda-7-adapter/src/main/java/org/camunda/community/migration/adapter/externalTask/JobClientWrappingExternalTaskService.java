/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.externalTask;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

public class JobClientWrappingExternalTaskService implements ExternalTaskService {
  private final JobClient client;
  private final ExternalTask task;

  public JobClientWrappingExternalTaskService(JobClient client, ExternalTask task) {
    this.client = client;
    this.task = task;
  }

  @Override
  public void lock(String externalTaskId, long lockDuration) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void lock(ExternalTask externalTask, long lockDuration) {
    lock(externalTask.getId(), lockDuration);
  }

  @Override
  public void unlock(ExternalTask externalTask) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void complete(ExternalTask externalTask) {
    complete(externalTask.getId(), new HashMap<>(), new HashMap<>());
  }

  @Override
  public void setVariables(String processInstanceId, Map<String, Object> variables) {
    setVariables(Optional.ofNullable(processInstanceId), false, variables);
  }

  private void setVariables(
      Optional<String> processInstanceId, boolean local, Map<String, Object> variables) {
    if (variables != null && !variables.isEmpty()) {
      if (client instanceof CamundaClient) {
        ((CamundaClient) client)
            .newSetVariablesCommand(
                Long.parseLong(processInstanceId.orElse(task.getProcessInstanceId())))
            .variables(variables)
            .local(local)
            .send()
            .join();
      } else {
        throw new UnsupportedOperationException();
      }
    }
  }

  @Override
  public void setVariables(ExternalTask externalTask, Map<String, Object> variables) {
    setVariables(Optional.ofNullable(externalTask.getProcessInstanceId()), false, variables);
  }

  @Override
  public void complete(ExternalTask externalTask, Map<String, Object> variables) {
    complete(externalTask.getId(), variables, null);
  }

  @Override
  public void complete(
      ExternalTask externalTask,
      Map<String, Object> variables,
      Map<String, Object> localVariables) {
    complete(externalTask.getId(), variables, localVariables);
  }

  @Override
  public void complete(
      String externalTaskId, Map<String, Object> variables, Map<String, Object> localVariables) {

    if (localVariables != null && !localVariables.isEmpty()) {
      setVariables(Optional.empty(), true, localVariables);
    }
    client.newCompleteCommand(Long.parseLong(externalTaskId)).variables(variables).send().join();
  }

  @Override
  public void handleFailure(
      ExternalTask externalTask,
      String errorMessage,
      String errorDetails,
      int retries,
      long retryTimeout) {
    handleFailure(
        externalTask.getId(), errorMessage, errorDetails, retries, retryTimeout, null, null);
  }

  @Override
  public void handleFailure(
      String externalTaskId,
      String errorMessage,
      String errorDetails,
      int retries,
      long retryTimeout) {
    handleFailure(externalTaskId, errorMessage, errorDetails, retries, retryTimeout, null, null);
  }

  @Override
  public void handleFailure(
      String externalTaskId,
      String errorMessage,
      String errorDetails,
      int retries,
      long retryTimeout,
      Map<String, Object> variables,
      Map<String, Object> localVariables) {
    final String composedErrorMessage = errorMessage + "\n\n" + errorDetails;
    setVariables(Optional.empty(), false, variables);
    setVariables(Optional.empty(), true, localVariables);
    client
        .newFailCommand(Long.parseLong(externalTaskId))
        .retries(retries)
        .errorMessage(composedErrorMessage)
        .requestTimeout(Duration.ofMillis(retryTimeout))
        .send()
        .join();
  }

  @Override
  public void handleBpmnError(ExternalTask externalTask, String errorCode) {
    handleBpmnError(externalTask.getId(), errorCode, null, null);
  }

  @Override
  public void handleBpmnError(ExternalTask externalTask, String errorCode, String errorMessage) {
    handleBpmnError(externalTask.getId(), errorCode, errorMessage, null);
  }

  @Override
  public void handleBpmnError(
      ExternalTask externalTask,
      String errorCode,
      String errorMessage,
      Map<String, Object> variables) {
    if (variables != null) {
      setVariables(externalTask, variables);
    }
    handleBpmnError(externalTask.getId(), errorCode, errorMessage, variables);
  }

  @Override
  public void handleBpmnError(
      String externalTaskId, String errorCode, String errorMessage, Map<String, Object> variables) {
    setVariables(Optional.empty(), false, variables);
    client
        .newThrowErrorCommand(Long.parseLong(externalTaskId))
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .send()
        .join();
  }

  @Override
  public void extendLock(ExternalTask externalTask, long newDuration) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void extendLock(String externalTaskId, long newDuration) {
    throw new UnsupportedOperationException();
  }
}
