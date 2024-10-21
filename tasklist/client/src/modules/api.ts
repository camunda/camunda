/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from './types';
import {mergePathname} from './utils/mergePathname';
import {
  endpoints,
  type QueryUserTasksRequestBody,
  type UserTask,
  type Form,
} from '@vzeta/camunda-api-zod-schemas/tasklist';

const BASENAME = window.clientConfig?.contextPath ?? '/';
const BASE_REQUEST_OPTIONS: RequestInit = {
  credentials: 'include',
  mode: 'cors',
};

function getFullURL(url: string) {
  if (typeof window.location.origin !== 'string') {
    throw new Error('window.location.origin is not a set');
  }

  return new URL(mergePathname(BASENAME, url), window.location.origin);
}

const api = {
  login: (body: {username: string; password: string}) => {
    return new Request(getFullURL('/api/login'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body: new URLSearchParams(body).toString(),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
  },
  logout: () =>
    new Request(getFullURL('/api/logout'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    }),
  startProcess: (payload: {
    bpmnProcessId: string;
    variables: Variable[];
    tenantId?: UserTask['tenantId'];
  }) => {
    const {bpmnProcessId, variables, tenantId} = payload;
    const url = getFullURL(`/v1/internal/processes/${bpmnProcessId}/start`);

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
  getProcess: (params: {processDefinitionId: number}) => {
    const url = getFullURL(
      `/v1/internal/processes/${params.processDefinitionId}`,
    );
    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getProcesses: (params: {
    query?: string;
    tenantId?: UserTask['tenantId'];
    isStartedByForm?: boolean;
  }) => {
    const url = getFullURL('/v1/internal/processes');

    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) {
        if (typeof value === 'string' && value !== '') {
          url.searchParams.set(key, value);
        } else if (typeof value === 'boolean') {
          url.searchParams.set(key, value ? 'true' : 'false');
        }
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
      getFullURL(`/internal/users/${userId}/process-instances`),
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
    new Request(getFullURL('/v1/internal/users/current'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    }),
  getForm: ({formKey}: Pick<Form, 'formKey'>) => {
    return new Request(getFullURL(endpoints.getForm.getUrl({formKey})), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.getForm.method,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getFullVariable: (variableId: Variable['id']) => {
    return new Request(getFullURL(`/v1/variables/${variableId}`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getAllVariables: ({userTaskKey}: {userTaskKey: UserTask['userTaskKey']}) => {
    return new Request(
      getFullURL(`/v1/tasks/${userTaskKey}/variables/search`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'POST',
        body: JSON.stringify({variableNames: []}),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  searchVariables: ({
    userTaskKey,
    variableNames,
  }: {
    userTaskKey: UserTask['userTaskKey'];
    variableNames: string[];
  }) => {
    const body = {
      includeVariables: variableNames.map((name) => ({
        name,
        alwaysReturnFullValue: true,
      })),
    };

    return new Request(
      getFullURL(`/v1/tasks/${userTaskKey}/variables/search`),
      {
        ...BASE_REQUEST_OPTIONS,
        method: 'POST',
        body: JSON.stringify(body),
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
  searchTasks: (body: QueryUserTasksRequestBody) => {
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
  unassignTask: (userTaskKey: UserTask['userTaskKey']) => {
    return new Request(getFullURL(`/v1/tasks/${userTaskKey}/unassign`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  assignTask: (userTaskKey: UserTask['userTaskKey']) => {
    return new Request(getFullURL(`/v1/tasks/${userTaskKey}/assign`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  completeTask: ({
    userTaskKey,
    ...body
  }: {
    userTaskKey: UserTask['userTaskKey'];
    variables: Pick<Variable, 'name' | 'value'>[];
  }) => {
    return new Request(getFullURL(`/v1/tasks/${userTaskKey}/complete`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    });
  },
  getTask: (userTaskKey: UserTask['userTaskKey']) => {
    return new Request(getFullURL(`/v1/tasks/${userTaskKey}`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getExternalForm: (bpmnProcessId: string) => {
    return new window.Request(
      getFullURL(`/v1/external/process/${bpmnProcessId}/form`),
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
      getFullURL(`/v1/external/process/${bpmnProcessId}/start`),
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
  getSaasUserToken: () => {
    return new Request(getFullURL('/v1/internal/users/token'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getLicense: () => {
    return new Request(getFullURL('/v2/license'), {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
} as const;

export {api};
