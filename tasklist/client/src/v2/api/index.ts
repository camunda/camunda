/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints as tasklistEndpoints,
  type CompleteTaskRequestBody,
  type QueryUserTasksRequestBody,
  type UserTask,
  type QueryVariablesByUserTaskRequestBody,
} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {
  endpoints as operateEndpoints,
  type QueryProcessDefinitionsRequestBody,
  type ProcessDefinition,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {endpoints as processManagementEndpoints} from '@vzeta/camunda-api-zod-schemas/process-management';
import {BASE_REQUEST_OPTIONS, getFullURL} from 'common/api';

const api = {
  queryTasks: (body: QueryUserTasksRequestBody = {}) => {
    return new Request(getFullURL(tasklistEndpoints.queryUserTasks.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: tasklistEndpoints.queryUserTasks.method,
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
  },
  queryProcesses: (body: QueryProcessDefinitionsRequestBody = {}) => {
    return new Request(
      getFullURL(operateEndpoints.queryProcessDefinitions.getUrl()),
      {
        ...BASE_REQUEST_OPTIONS,
        method: operateEndpoints.queryProcessDefinitions.method,
        body: JSON.stringify(body),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  getProcessDefinitionXml: (
    body: Pick<ProcessDefinition, 'processDefinitionKey'>,
  ) => {
    return new Request(
      getFullURL(operateEndpoints.getProcessDefinitionXml.getUrl(body)),
      {
        ...BASE_REQUEST_OPTIONS,
        method: operateEndpoints.getProcessDefinitionXml.method,
      },
    );
  },
  getTask: (body: Pick<UserTask, 'userTaskKey'>) => {
    return new Request(getFullURL(tasklistEndpoints.getTask.getUrl(body)), {
      ...BASE_REQUEST_OPTIONS,
      method: tasklistEndpoints.getTask.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  assignTask: (params: Pick<UserTask, 'userTaskKey'> & {assignee: string}) => {
    const {userTaskKey, ...body} = params;
    return new Request(
      getFullURL(tasklistEndpoints.assignTask.getUrl({userTaskKey})),
      {
        ...BASE_REQUEST_OPTIONS,
        method: tasklistEndpoints.assignTask.method,
        body: JSON.stringify(body),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  unassignTask: (body: Pick<UserTask, 'userTaskKey'>) => {
    return new Request(
      getFullURL(tasklistEndpoints.unassignTask.getUrl(body)),
      {
        ...BASE_REQUEST_OPTIONS,
        method: tasklistEndpoints.unassignTask.method,
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  queryVariablesByUserTask: (
    params: Pick<UserTask, 'userTaskKey'> & QueryVariablesByUserTaskRequestBody,
  ) => {
    const {userTaskKey, ...body} = params;
    return new Request(
      getFullURL(
        tasklistEndpoints.queryVariablesByUserTask.getUrl({userTaskKey}),
      ),
      {
        ...BASE_REQUEST_OPTIONS,
        method: tasklistEndpoints.queryVariablesByUserTask.method,
        body: JSON.stringify(body),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  completeTask: (
    params: Pick<UserTask, 'userTaskKey'> & CompleteTaskRequestBody,
  ) => {
    const {userTaskKey, variables, ...body} = params;

    return new Request(
      getFullURL(tasklistEndpoints.completeTask.getUrl({userTaskKey})),
      {
        ...BASE_REQUEST_OPTIONS,
        method: tasklistEndpoints.completeTask.method,
        body: JSON.stringify({...body, variables}),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  getVariable: (variableKey: string) => {
    return new Request(
      getFullURL(processManagementEndpoints.getVariable.getUrl({variableKey})),
      {
        ...BASE_REQUEST_OPTIONS,
        method: processManagementEndpoints.getVariable.method,
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
} as const;

export {api};
