/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type CompleteTaskRequestBody,
  type QueryUserTasksRequestBody,
  type UserTask,
  type QueryVariablesByUserTaskRequestBody,
  type QueryProcessDefinitionsRequestBody,
  type ProcessDefinition,
  type CreateProcessInstanceRequestBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {BASE_REQUEST_OPTIONS, getFullURL} from 'common/api';

const api = {
  queryTasks: (body: QueryUserTasksRequestBody = {}) => {
    return new Request(getFullURL(endpoints.queryUserTasks.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.queryUserTasks.method,
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
  },
  queryProcesses: (body: QueryProcessDefinitionsRequestBody = {}) => {
    return new Request(getFullURL(endpoints.queryProcessDefinitions.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.queryProcessDefinitions.method,
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getProcessDefinitionXml: (
    body: Pick<ProcessDefinition, 'processDefinitionKey'>,
  ) => {
    return new Request(
      getFullURL(endpoints.getProcessDefinitionXml.getUrl(body)),
      {
        ...BASE_REQUEST_OPTIONS,
        method: endpoints.getProcessDefinitionXml.method,
      },
    );
  },
  getTask: (body: Pick<UserTask, 'userTaskKey'>) => {
    return new Request(getFullURL(endpoints.getTask.getUrl(body)), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.getTask.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  assignTask: (params: Pick<UserTask, 'userTaskKey'> & {assignee: string}) => {
    const {userTaskKey, ...body} = params;
    return new Request(getFullURL(endpoints.assignTask.getUrl({userTaskKey})), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.assignTask.method,
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  unassignTask: (body: Pick<UserTask, 'userTaskKey'>) => {
    return new Request(getFullURL(endpoints.unassignTask.getUrl(body)), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.unassignTask.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  queryVariablesByUserTask: (
    params: Pick<UserTask, 'userTaskKey'> & QueryVariablesByUserTaskRequestBody,
  ) => {
    const {userTaskKey, ...body} = params;
    return new Request(
      getFullURL(endpoints.queryVariablesByUserTask.getUrl({userTaskKey})),
      {
        ...BASE_REQUEST_OPTIONS,
        method: endpoints.queryVariablesByUserTask.method,
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
      getFullURL(endpoints.completeTask.getUrl({userTaskKey})),
      {
        ...BASE_REQUEST_OPTIONS,
        method: endpoints.completeTask.method,
        body: JSON.stringify({...body, variables}),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  getVariable: (variableKey: string) => {
    return new Request(
      getFullURL(endpoints.getVariable.getUrl({variableKey})),
      {
        ...BASE_REQUEST_OPTIONS,
        method: endpoints.getVariable.method,
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  getUserTaskForm: (params: Pick<UserTask, 'userTaskKey'>) => {
    return new Request(getFullURL(endpoints.getUserTaskForm.getUrl(params)), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.getUserTaskForm.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  createProcessInstance: (params: CreateProcessInstanceRequestBody) => {
    return new Request(getFullURL(endpoints.createProcessInstance.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.createProcessInstance.method,
      body: JSON.stringify(params),
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getSaasUserToken: () => {
    return new Request(getFullURL('/v2/authentication/me/token'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getProcessStartForm: (
    body: Pick<ProcessDefinition, 'processDefinitionKey'>,
  ) => {
    return new Request(getFullURL(endpoints.getProcessStartForm.getUrl(body)), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.getProcessStartForm.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
} as const;

export {api};
