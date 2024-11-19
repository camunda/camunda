/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type NonEmptyArray<T> = [T, ...T[]];

type Permissions = NonEmptyArray<'read' | 'write'>;

type CurrentUser = {
  userId: string;
  displayName: string | null;
  permissions: Permissions;
  roles: string[] | null;
  salesPlanType: string | null;
  c8Links: {
    name: 'console' | 'modeler' | 'tasklist' | 'operate' | 'optimize';
    link: string;
  }[];
  tenants: {
    id: string;
    name: string;
  }[];
  groups: string[];
};

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

type TaskState = 'CREATED' | 'COMPLETED' | 'CANCELED';

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

type License = {
  validLicense: boolean;
  licenseType: string;
  isCommercial: boolean;
  expiresAt: string | null;
};

export type {
  CurrentUser,
  Variable,
  Task,
  TaskState,
  Form,
  Permissions,
  Process,
  ProcessInstance,
  FullVariable,
  TruncatedVariable,
  License,
};
