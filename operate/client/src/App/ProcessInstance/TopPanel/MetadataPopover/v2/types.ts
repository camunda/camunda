/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {
  ElementInstance,
  QueryUserTasksResponseBody,
} from '@vzeta/camunda-api-zod-schemas';

type UserTaskMetadata = {
  dueDate?: string;
  followUpDate?: string;
  formKey?: string;
  assignee?: string;
  userTaskKey?: string;
  candidateGroups?: string[];
  candidateUsers?: string[];
  tenantId?: string;
  externalFormReference?: string;
  creationDate?: string;
  completionDate?: string;
  customHeaders?: Record<string, unknown>;
  priority?: number;
};

// V2 Element Instance Metadata - extends the old structure but with v2 element instance fields will be removed after other components migration
type V2InstanceMetadata = {
  elementInstanceKey: string;
  elementId: string;
  elementName?: string;
  type: ElementInstance['type'];
  state?: ElementInstance['state'];
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
  flowNodeInstanceId?: string;
  flowNodeId?: string;
  flowNodeType?: string;
  eventId?: string;
  jobType?: string | null;
  jobWorker?: string | null;
  jobDeadline?: string | null;
  jobCustomHeaders?: string | null;
  jobId?: string | null;
} & Partial<UserTaskMetadata>;

type V2MetaDataDto = Omit<MetaDataDto, 'instanceMetadata'> & {
  instanceMetadata: V2InstanceMetadata | null;
};

type UserTaskSubset = Pick<
  QueryUserTasksResponseBody['items'][number],
  | 'dueDate'
  | 'followUpDate'
  | 'formKey'
  | 'assignee'
  | 'userTaskKey'
  | 'candidateGroups'
  | 'candidateUsers'
  | 'tenantId'
  | 'externalFormReference'
  | 'creationDate'
  | 'completionDate'
  | 'customHeaders'
  | 'priority'
>;

// Utility function to create V2 instance metadata from old metadata + migrated element instance
function createV2InstanceMetadata(
  oldMetadata: MetaDataDto['instanceMetadata'],
  elementInstance: ElementInstance,
  userTask?: UserTaskSubset,
): V2InstanceMetadata {
  return {
    calledProcessInstanceId: oldMetadata?.calledProcessInstanceId ?? null,
    calledProcessDefinitionName:
      oldMetadata?.calledProcessDefinitionName ?? null,
    calledDecisionInstanceId: oldMetadata?.calledDecisionInstanceId ?? null,
    calledDecisionDefinitionName:
      oldMetadata?.calledDecisionDefinitionName ?? null,
    jobRetries: oldMetadata?.jobRetries ?? null,
    elementInstanceKey: elementInstance.elementInstanceKey,
    elementId: elementInstance.elementId,
    elementName: elementInstance.elementName,
    type: elementInstance.type,
    state: elementInstance.state,
    startDate: elementInstance.startDate || '',
    endDate: elementInstance.endDate || null,
    processDefinitionId: elementInstance.processDefinitionId,
    processInstanceKey: elementInstance.processInstanceKey,
    processDefinitionKey: elementInstance.processDefinitionKey,
    hasIncident: elementInstance.hasIncident,
    incidentKey: elementInstance.incidentKey,
    tenantId: elementInstance.tenantId,
    flowNodeInstanceId: elementInstance.elementInstanceKey,
    flowNodeId: elementInstance.elementId,
    flowNodeType: elementInstance.type,

    ...(userTask ?? {}),
  };
}

export type {V2InstanceMetadata, V2MetaDataDto, UserTaskSubset};
export {createV2InstanceMetadata};
