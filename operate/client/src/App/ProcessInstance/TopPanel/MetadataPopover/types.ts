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

type InstanceMetadata = {
  elementInstanceKey: string;
  elementId: string;
  elementName?: string;
  type: ElementInstance['type'];
  userTaskState?: UserTask['state'];
  elementInstanceState: ElementInstance['state'];
  startDate: string;
  endDate: string | null;
  processDefinitionId?: string;
  processInstanceKey?: string;
  processDefinitionKey?: string;
  hasIncident?: boolean;
  incidentKey?: string;
  tenantId?: string;
  calledProcessInstanceId: string | null;
  calledProcessDefinitionName: string | null;
  calledDecisionInstanceId: string | null;
  calledDecisionDefinitionName: string | null;
  jobRetries: number | null;
  elementType?: string;
  eventId?: string;
  jobType?: string | null;
  jobWorker?: string | null;
  jobDeadline?: string | null;
  jobCustomHeaders: Record<string, unknown> | null;
  jobKey?: string | null;
  messageName?: string | null;
  correlationKey?: string | null;
} & Partial<UserTask>;

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

function createInstanceMetadata(
  elementInstance: ElementInstance,
  job?: Job,
  calledProcessInstance?: ProcessInstance,
  messageSubscription?: MessageSubscription,
  calledDecisionDefinition?: DecisionDefinition,
  calledDecisionInstance?: DecisionInstance,
  userTask: Partial<UserTaskSubset> | null = {},
): InstanceMetadata {
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

  return {
    calledProcessInstanceId: calledProcessInstance?.processInstanceKey ?? null,
    calledProcessDefinitionName:
      calledProcessInstance?.processDefinitionName ?? null,
    calledDecisionInstanceId:
      calledDecisionInstance?.decisionEvaluationInstanceKey ?? null,
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
    incidentKey: elementInstance.incidentKey,
    tenantId: elementInstance.tenantId,
    elementType: elementInstance.type,
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
  };
}

export type {InstanceMetadata};

export {createInstanceMetadata};
