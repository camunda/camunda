/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Form, Task, TasksSearchBody, Variable} from './types';
import {mergePathname} from './utils/mergePathname';

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
} as const;

export {api};
