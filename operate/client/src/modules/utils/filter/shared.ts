/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ProcessInstanceFilterField =
  | 'process'
  | 'version'
  | 'ids'
  | 'businessKey'
  | 'parentInstanceId'
  | 'errorMessage'
  | 'incidentErrorHashCode'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValues'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled'
  | 'startDateAfter'
  | 'startDateBefore'
  | 'endDateAfter'
  | 'endDateBefore'
  | 'tenant'
  | 'retriesLeft';

type ProcessInstanceFilters = {
  process?: string;
  version?: string;
  ids?: string;
  businessKey?: string;
  parentInstanceId?: string;
  errorMessage?: string;
  incidentErrorHashCode?: number;
  flowNodeId?: string;
  variableName?: string;
  variableValues?: string;
  operationId?: string;
  active?: boolean;
  incidents?: boolean;
  completed?: boolean;
  canceled?: boolean;
  startDateAfter?: string;
  startDateBefore?: string;
  endDateAfter?: string;
  endDateBefore?: string;
  tenant?: string;
  retriesLeft?: boolean;
};

const PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'process',
  'version',
  'ids',
  'businessKey',
  'parentInstanceId',
  'errorMessage',
  'incidentErrorHashCode',
  'flowNodeId',
  'variableName',
  'variableValues',
  'operationId',
  'active',
  'incidents',
  'completed',
  'canceled',
  'startDateAfter',
  'startDateBefore',
  'endDateAfter',
  'endDateBefore',
  'tenant',
  'retriesLeft',
];

const BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'active',
  'incidents',
  'completed',
  'canceled',
  'retriesLeft',
];

export type {ProcessInstanceFilterField, ProcessInstanceFilters};

export {PROCESS_INSTANCE_FILTER_FIELDS, BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS};
