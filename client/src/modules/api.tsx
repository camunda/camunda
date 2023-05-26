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
  startProcess: (processDefinitionKey: string) => {
    return new Request(
      mergePathname(
        BASENAME,
        `/v1/internal/processes/${processDefinitionKey}/start`,
      ),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  getProcesses: (query?: string) => {
    const url = new URL(window.location.href);
    url.pathname = mergePathname(BASENAME, '/v1/internal/processes');

    if (query !== undefined && query !== '') {
      url.searchParams.set('query', query);
    }

    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
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
} as const;

export {api};
