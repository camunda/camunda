/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BASE_REQUEST_OPTIONS, getFullURL} from 'common/api';
import type {Form, Task, TasksSearchBody, Variable} from './types';

const api = {
  startProcess: (payload: {
    bpmnProcessId: string;
    variables: Pick<Variable, 'name' | 'value'>[];
    tenantId?: Task['tenantId'];
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
  getProcess: (params: {processDefinitionId: string}) => {
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
    tenantId?: Task['tenantId'];
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
    username: string;
    pageSize: number;
    searchAfter?: [string, string];
    searchBefore?: [string, string];
  }) => {
    const {username, ...body} = payload;

    return new Request(
      getFullURL(`/internal/users/${username}/process-instances`),
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
  getEmbeddedForm: ({
    id,
    processDefinitionKey,
  }: Pick<Form, 'id' | 'processDefinitionKey'>) => {
    const url = getFullURL(`/v1/forms/${id}`);

    url.searchParams.set('processDefinitionKey', processDefinitionKey);

    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  getDeployedForm: ({
    id,
    processDefinitionKey,
    version,
  }: Pick<Form, 'id' | 'processDefinitionKey'> & {
    version: NonNullable<Form['version']> | 'latest';
  }) => {
    const url = getFullURL(`/v1/forms/${id}`);

    url.searchParams.set('processDefinitionKey', processDefinitionKey);

    if (version !== 'latest') {
      url.searchParams.set('version', version.toString());
    }

    return new Request(url, {
      ...BASE_REQUEST_OPTIONS,
      method: 'GET',
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
  getAllVariables: ({taskId}: {taskId: Task['id']}) => {
    return new Request(getFullURL(`/v1/tasks/${taskId}/variables/search`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body: JSON.stringify({variableNames: []}),
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
    const body = {
      includeVariables: variableNames.map((name) => ({
        name,
        alwaysReturnFullValue: true,
      })),
    };

    return new Request(getFullURL(`/v1/tasks/${taskId}/variables/search`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'POST',
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  searchTasks: (body: TasksSearchBody) => {
    return new Request(getFullURL('/v1/tasks/search'), {
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
    return new Request(getFullURL(`/v1/tasks/${taskId}/unassign`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
    });
  },
  assignTask: (taskId: Task['id']) => {
    return new Request(getFullURL(`/v1/tasks/${taskId}/assign`), {
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
    return new Request(getFullURL(`/v1/tasks/${taskId}/complete`), {
      ...BASE_REQUEST_OPTIONS,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    });
  },
  getTask: (taskId: Task['id']) => {
    return new Request(getFullURL(`/v1/tasks/${taskId}`), {
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
    variables: Pick<Variable, 'name' | 'value'>[];
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
} as const;

export {api};
