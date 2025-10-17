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
  DecisionInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
// V2 Element Instance Metadata - aggregated type created from v2 APIs
type V2InstanceMetadata = {
  elementInstanceKey: string;
  elementId: string;
  elementName?: string;
  type: ElementInstance['type'];
  state?: ElementInstance['state'] | UserTask['state'];
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
  eventId?: string;
  jobType?: string | null;
  jobWorker?: string | null;
  jobDeadline?: string | null;
  jobCustomHeaders: Record<string, unknown> | null;
  jobKey?: string | null;
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

// Utility function to create V2 instance metadata by aggregating data from v2 APIs
function buildInstanceMetadata(
  elementInstance: ElementInstance,
  job?: Job,
  calledProcess?: ProcessInstance,
  decisionInstance?: DecisionInstance,
  userTask: Partial<UserTaskSubset> | null = {},
): V2InstanceMetadata {
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

  return {
    calledProcessInstanceId: calledProcess?.processInstanceKey ?? null,
    calledProcessDefinitionName: calledProcess?.processDefinitionName ?? null,
    calledDecisionInstanceId:
      decisionInstance?.decisionEvaluationInstanceKey ?? null,
    calledDecisionDefinitionName:
      decisionInstance?.decisionDefinitionName ?? null,
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
    state: (userTaskState ??
      elementInstance.state) as V2InstanceMetadata['state'],
    startDate: elementInstance.startDate || '',
    endDate: elementInstance.endDate || null,
    processDefinitionId: elementInstance.processDefinitionId,
    processInstanceKey: elementInstance.processInstanceKey,
    processDefinitionKey: elementInstance.processDefinitionKey,
    hasIncident: elementInstance.hasIncident,
    incidentKey: elementInstance.incidentKey,
    tenantId: elementInstance.tenantId,

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
  };
}

export type {V2InstanceMetadata};

export {buildInstanceMetadata};
