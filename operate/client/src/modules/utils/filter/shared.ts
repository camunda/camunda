/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ProcessInstanceFilterField =
  | 'processDefinitionId'
  | 'processDefinitionVersion'
  | 'processInstanceKey'
  | 'parentProcessInstanceKey'
  | 'errorMessage'
  | 'incidentErrorHashCode'
  | 'elementId'
  | 'variableName'
  | 'variableValues'
  | 'batchOperationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled'
  | 'startDateFrom'
  | 'startDateTo'
  | 'endDateFrom'
  | 'endDateTo'
  | 'tenantId'
  | 'hasRetriesLeft';

type ProcessInstanceFilters = {
  processDefinitionId?: string;
  processDefinitionVersion?: string;
  processInstanceKey?: string;
  parentProcessInstanceKey?: string;
  errorMessage?: string;
  incidentErrorHashCode?: number;
  elementId?: string;
  variableName?: string;
  variableValues?: string;
  batchOperationId?: string;
  active?: boolean;
  incidents?: boolean;
  completed?: boolean;
  canceled?: boolean;
  startDateFrom?: string;
  startDateTo?: string;
  endDateFrom?: string;
  endDateTo?: string;
  tenantId?: string;
  hasRetriesLeft?: boolean;
};

const PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'processDefinitionId',
  'processDefinitionVersion',
  'processInstanceKey',
  'parentProcessInstanceKey',
  'errorMessage',
  'incidentErrorHashCode',
  'elementId',
  'variableName',
  'variableValues',
  'batchOperationId',
  'active',
  'incidents',
  'completed',
  'canceled',
  'startDateFrom',
  'startDateTo',
  'endDateFrom',
  'endDateTo',
  'tenantId',
  'hasRetriesLeft',
];

const BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'active',
  'incidents',
  'completed',
  'canceled',
  'hasRetriesLeft',
];

export type {ProcessInstanceFilterField, ProcessInstanceFilters};

export {PROCESS_INSTANCE_FILTER_FIELDS, BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS};
