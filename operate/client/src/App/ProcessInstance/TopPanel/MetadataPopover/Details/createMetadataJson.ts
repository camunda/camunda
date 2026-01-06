/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ElementInstance,
  ProcessInstance,
  Job,
  UserTask,
  MessageSubscription,
  DecisionDefinition,
  DecisionInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';

type UserTaskSubset = Pick<
  UserTask,
  | 'dueDate'
  | 'followUpDate'
  | 'formKey'
  | 'assignee'
  | 'userTaskKey'
  | 'candidateGroups'
  | 'candidateUsers'
  | 'state'
  | 'externalFormReference'
  | 'creationDate'
  | 'completionDate'
  | 'customHeaders'
  | 'priority'
>;

/**
 * Creates a JSON string representation of element instance metadata
 * for display in the metadata modal.
 *
 * This function merges data from multiple sources (element instance, job,
 * process instances, message subscriptions, decision instances, user tasks,
 * and incidents) into a single JSON string.
 */
export function createMetadataJson(
  elementInstance: ElementInstance,
  incident: {
    errorTypeName: string;
    errorMessage: string;
  } | null,
  job?: Job,
  calledProcessInstance?: ProcessInstance,
  messageSubscription?: MessageSubscription,
  calledDecisionDefinition?: DecisionDefinition,
  calledDecisionInstance?: DecisionInstance,
  userTask?: Partial<UserTaskSubset> | null,
): string {
  const {
    creationDate,
    completionDate,
    customHeaders,
    priority,
    userTaskKey,
    state: userTaskState,
    dueDate,
    followUpDate,
    formKey,
    assignee,
    candidateGroups,
    candidateUsers,
    externalFormReference,
  } = userTask ?? {};

  const {messageName, correlationKey} = messageSubscription ?? {};

  return JSON.stringify({
    calledProcessDefinitionName:
      calledProcessInstance?.processDefinitionName ?? null,
    calledDecisionDefinitionName:
      calledDecisionInstance?.decisionDefinitionName ||
      calledDecisionDefinition?.name ||
      null,
    jobRetries: job?.retries ?? null,
    jobDeadline: job?.deadline ?? null,
    jobKey: job?.jobKey ?? null,
    jobType: job?.type ?? null,
    jobWorker: job?.worker ?? null,
    jobCustomHeaders: job?.customHeaders ?? null,
    elementInstanceKey: elementInstance.elementInstanceKey,
    elementId: elementInstance.elementId,
    elementName: elementInstance.elementName,
    type: elementInstance.type,
    elementInstanceState: elementInstance.state,
    userTaskState: userTaskState,
    startDate: elementInstance.startDate || '',
    endDate: elementInstance.endDate || null,
    processDefinitionId: elementInstance.processDefinitionId,
    processInstanceKey: elementInstance.processInstanceKey,
    processDefinitionKey: elementInstance.processDefinitionKey,
    hasIncident: elementInstance.hasIncident,
    tenantId: elementInstance.tenantId,
    elementType: elementInstance.type,
    incidentErrorType: incident?.errorTypeName || null,
    incidentErrorMessage: incident?.errorMessage || null,
    calledProcessInstanceKey: calledProcessInstance?.processInstanceKey ?? null,
    calledDecisionInstanceKey:
      calledDecisionInstance?.decisionEvaluationInstanceKey ?? null,
    creationDate,
    completionDate,
    customHeaders,
    priority,
    userTaskKey,
    dueDate,
    followUpDate,
    formKey,
    assignee,
    candidateGroups,
    candidateUsers,
    externalFormReference,
    messageName,
    correlationKey,
  });
}
