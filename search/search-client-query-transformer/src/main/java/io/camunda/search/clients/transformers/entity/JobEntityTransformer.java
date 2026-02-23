/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;

public class JobEntityTransformer
    implements ServiceTransformer<io.camunda.webapps.schema.entities.JobEntity, JobEntity> {

  @Override
  public JobEntity apply(final io.camunda.webapps.schema.entities.JobEntity value) {
    return new JobEntity.Builder()
        .jobKey(value.getKey())
        .type(value.getType())
        .worker(value.getWorker())
        .state(toState(value.getState()))
        .kind(toJobKind(value.getJobKind()))
        .listenerEventType(toListenerEventType(value.getListenerEventType()))
        .retries(value.getRetries())
        .isDenied(value.isDenied())
        .deniedReason(value.getDeniedReason())
        .hasFailedWithRetriesLeft(value.isJobFailedWithRetriesLeft())
        .errorCode(value.getErrorCode())
        .errorMessage(value.getErrorMessage())
        .customHeaders(value.getCustomHeaders())
        .deadline(value.getDeadline())
        .endTime(value.getEndTime())
        .processDefinitionId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processInstanceKey(value.getProcessInstanceKey())
        .rootProcessInstanceKey(value.getRootProcessInstanceKey())
        .elementId(value.getFlowNodeId())
        .elementInstanceKey(value.getFlowNodeInstanceId())
        .tenantId(value.getTenantId())
        .creationTime(value.getCreationTime())
        .lastUpdateTime(value.getLastUpdateTime())
        .build();
  }

  private ListenerEventType toListenerEventType(final String value) {
    if (value == null) {
      return null;
    }

    return switch (value) {
      case "UNSPECIFIED" -> ListenerEventType.UNSPECIFIED;
      case "START" -> ListenerEventType.START;
      case "END" -> ListenerEventType.END;
      case "CREATING" -> ListenerEventType.CREATING;
      case "ASSIGNING" -> ListenerEventType.ASSIGNING;
      case "UPDATING" -> ListenerEventType.UPDATING;
      case "COMPLETING" -> ListenerEventType.COMPLETING;
      case "CANCELING" -> ListenerEventType.CANCELING;
      default -> throw new IllegalArgumentException("Unknown listener event type: " + value);
    };
  }

  private JobKind toJobKind(final String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "BPMN_ELEMENT" -> JobKind.BPMN_ELEMENT;
      case "EXECUTION_LISTENER" -> JobKind.EXECUTION_LISTENER;
      case "TASK_LISTENER" -> JobKind.TASK_LISTENER;
      case "AD_HOC_SUB_PROCESS" -> JobKind.AD_HOC_SUB_PROCESS;
      default -> throw new IllegalArgumentException("Unknown job kind: " + value);
    };
  }

  private JobState toState(final String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "CREATED" -> JobState.CREATED;
      case "COMPLETED" -> JobState.COMPLETED;
      case "FAILED" -> JobState.FAILED;
      case "RETRIES_UPDATED" -> JobState.RETRIES_UPDATED;
      case "TIMED_OUT" -> JobState.TIMED_OUT;
      case "CANCELED" -> JobState.CANCELED;
      case "ERROR_THROWN" -> JobState.ERROR_THROWN;
      case "MIGRATED" -> JobState.MIGRATED;
      default -> throw new IllegalArgumentException("Unknown job state: " + value);
    };
  }
}
