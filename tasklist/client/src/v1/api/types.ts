/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type FullVariable = {
  id: string;
  name: string;
  value: string;
  previewValue: string;
  isValueTruncated: false;
};

type TruncatedVariable = {
  id: string;
  name: string;
  value: null;
  previewValue: string;
  isValueTruncated: true;
};

type Variable = FullVariable | TruncatedVariable;

type TaskState =
  | 'CREATED'
  | 'COMPLETED'
  | 'CANCELED'
  | 'FAILED'
  | 'ASSIGNING'
  | 'UPDATING'
  | 'COMPLETING'
  | 'CANCELING';

type Task = {
  id: string;
  name: string;
  taskDefinitionId: string;
  processName: string;
  creationDate: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionDate: string | null;
  priority: number | null;
  assignee: string | null;
  taskState: TaskState;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  formVersion: number | null | undefined;
  formId: string | null;
  isFormEmbedded: boolean | null;
  processInstanceKey: string;
  processDefinitionKey: string;
  candidateGroups: string[];
  candidateUsers: string[];
  tenantId: string | '<default>';
  context: string | null;
};

type Form = {
  id: string;
  processDefinitionKey: string;
  schema: string;
  title: string;
  version: number | null;
  tenantId: string;
  isDeleted: boolean;
};

type Process = {
  id: string;
  name: string | null;
  bpmnProcessId: string;
  version: number;
  startEventFormId: string | null;
  sortValues: [string];
  bpmnXml: string | null;
};

type ProcessInstance = {
  id: string;
  process: Process;
  state: 'active' | 'completed' | 'canceled' | 'incident' | 'terminated';
  creationDate: string;
  sortValues: [string, string];
  isFirst: boolean;
};

type DateSearch = {
  from?: string;
  to?: string;
};

type PaginationSearchPair = [string, string];

type TasksSearchBody = {
  state?: TaskState;
  followUpDate?: DateSearch;
  dueDate?: DateSearch;
  assigned?: boolean;
  assignee?: string;
  taskDefinitionId?: string;
  candidateGroup?: string;
  candidateUser?: string;
  processDefinitionKey?: string;
  processInstanceKey?: string;
  pageSize?: number;
  sort?: Array<{
    field:
      | 'completionTime'
      | 'creationTime'
      | 'followUpDate'
      | 'dueDate'
      | 'priority';
    order: 'ASC' | 'DESC';
  }>;
  searchAfter?: PaginationSearchPair;
  searchAfterOrEqual?: PaginationSearchPair;
  searchBefore?: PaginationSearchPair;
  searchBeforeOrEqual?: PaginationSearchPair;
  tenantIds?: string[];
  taskVariables?: Array<{
    name: string;
    value: string;
    operator: 'eq';
  }>;
};

export type {
  Variable,
  Task,
  TaskState,
  Form,
  Process,
  ProcessInstance,
  TasksSearchBody,
  FullVariable,
  TruncatedVariable,
};
