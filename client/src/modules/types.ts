/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type OperationType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_WORKFLOW_INSTANCE'
  | 'UPDATE_VARIABLE';

type InstanceState =
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELED'
  | 'INCIDENT'
  | 'TERMINATED';

type Operation = {
  id: string;
  type: OperationType;
  state: InstanceState;
  errorMessage: null | string;
};

type Instance = {
  bpmnProcessId: string;
  endDate: null | string;
  hasActiveOperation: boolean;
  id: string;
  operations: Operation[];
  startDate: string;
  state: InstanceState;
  workflowId: string;
  workflowName: string;
  workflowVersion: number;
};

export type {OperationType, InstanceState, Operation, Instance};
