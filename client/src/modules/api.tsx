/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form, Task, TasksSearchBody, Variable} from './types';
import {mergePathname} from './utils/mergePathname';

const BASENAME = window.clientConfig?.contextPath ?? '/';
const BASE_REQUEST_OPTIONS: RequestInit = {
  credentials: 'include',
  mode: 'cors',
};

const api = {
  login: (body: {username: string; password: string}) => {
    return new Request(mergePathname(BASENAME, '/api/login'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body: new URLSearchParams(body).toString(),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
  },
  logout: () =>
    new Request(mergePathname(BASENAME, '/api/logout'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    }),
  startProcess: (payload: {
    bpmnProcessId: string;
    variables: Variable[];
    tenantId?: Task['tenantId'];
  }) => {
    const {bpmnProcessId, variables, tenantId} = payload;
    const url = new URL(window.location.origin);
    url.pathname = mergePathname(
      BASENAME,
      `/v1/internal/processes/${bpmnProcessId}/start`,
    );

    if (tenantId !== undefined) {
      url.searchParams.set('tenantId', tenantId);
    }

    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      body: JSON.stringify({variables}),
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getProcesses: (params: {query?: string; tenantId?: Task['tenantId']}) => {
    const url = new URL(window.location.origin);
    url.pathname = mergePathname(BASENAME, '/v1/internal/processes');

    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, value);
      }
    });

    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
  },
  searchProcessInstances: (payload: {
    userId: string;
    pageSize: number;
    searchAfter?: [string, string];
    searchBefore?: [string, string];
  }) => {
    const {userId, ...body} = payload;

    return new Request(
      mergePathname(BASENAME, `/internal/users/${userId}/process-instances`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'POST',
        body: JSON.stringify(body),
        headers: {
          'Content-Type': 'application/json',
          'x-is-polling': 'true',
        },
      },
    );
  },
  getCurrentUser: () =>
    new Request(mergePathname(BASENAME, '/v1/internal/users/current'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    }),
  getForm: ({
    id,
    processDefinitionKey,
  }: Pick<Form, 'id' | 'processDefinitionKey'>) => {
    const url = new URL(window.location.href);

    url.pathname = mergePathname(BASENAME, `/v1/forms/${id}`);
    url.searchParams.set('processDefinitionKey', processDefinitionKey);

    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getFullVariable: (variableId: Variable['id']) => {
    return new Request(mergePathname(BASENAME, `/v1/variables/${variableId}`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  searchVariables: ({
    taskId,
    variableNames,
  }: {
    taskId: Task['id'];
    variableNames: Task['name'][];
  }) => {
    return new Request(
      mergePathname(BASENAME, `/v1/tasks/${taskId}/variables/search`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'POST',
        body: JSON.stringify({variableNames}),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  searchTasks: (body: TasksSearchBody) => {
    return new Request(mergePathname(BASENAME, '/v1/tasks/search'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
  },
  unassignTask: (taskId: Task['id']) => {
    return new Request(
      mergePathname(BASENAME, `/v1/tasks/${taskId}/unassign`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  assignTask: (taskId: Task['id']) => {
    return new Request(mergePathname(BASENAME, `/v1/tasks/${taskId}/assign`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  completeTask: ({
    taskId,
    ...body
  }: {
    taskId: Task['id'];
    variables: Pick<Variable, 'name' | 'value'>[];
  }) => {
    return new Request(
      mergePathname(BASENAME, `/v1/tasks/${taskId}/complete`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
      },
    );
  },
  getTask: (taskId: Task['id']) => {
    return new Request(mergePathname(BASENAME, `/v1/tasks/${taskId}`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getExternalForm: (bpmnProcessId: string) => {
    return new Request(
      mergePathname(BASENAME, `/v1/external/process/${bpmnProcessId}/form`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  startExternalProcess: ({
    bpmnProcessId,
    variables,
  }: {
    bpmnProcessId: string;
    variables: Variable[];
  }) => {
    return new Request(
      mergePathname(BASENAME, `/v1/external/process/${bpmnProcessId}/start`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({variables}),
      },
    );
  },
} as const;

export {api};
