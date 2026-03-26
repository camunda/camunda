/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {z} from 'zod';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ListenersTab} from './index';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {searchResult} from 'modules/testUtils';
import {
  endpoints,
  type QueryJobsResponseBody,
  type ElementInstance,
  type Job,
  type ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';

function mockValidatedSearchJobs(
  schema: z.ZodType,
  data: QueryJobsResponseBody,
) {
  mockServer.use(
    http.post(
      endpoints.queryJobs.getUrl(),
      async ({request}) => {
        const body = await request.json();
        const result = schema.safeParse(body);

        if (!result.success) {
          return HttpResponse.error();
        }

        return HttpResponse.json(data);
      },
      {once: true},
    ),
  );
}

function buildExpectedPayload(kind: z.ZodType) {
  return z.strictObject({
    filter: z.strictObject({
      processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
      elementId: z.literal('Task_1'),
      elementInstanceKey: z.literal('123456789'),
      kind,
    }),
    page: z.strictObject({
      limit: z.literal(50),
      from: z.literal(0),
    }),
  });
}

const PROCESS_INSTANCE_ID = '111222333';
const PROCESS_DEFINITION_KEY = '444555666';

const mockProcessInstance = {
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionId: 'process-def-1',
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  processDefinitionName: 'Main Process',
  processDefinitionVersion: 1,
  state: 'ACTIVE',
  startDate: '2023-01-15T10:00:00.000Z',
  tenantId: '<default>',
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  hasIncident: false,
  rootProcessInstanceKey: null,
  tags: [],
  processDefinitionVersionTag: null,
  endDate: null,
} satisfies ProcessInstance;

const mockElementInstance = {
  elementInstanceKey: '123456789',
  elementId: 'Task_1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2023-01-15T10:00:00.000Z',
  endDate: '2023-01-15T10:05:00.000Z',
  processDefinitionId: 'process-def-1',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  hasIncident: false,
  tenantId: '<default>',
  rootProcessInstanceKey: null,
  incidentKey: null,
} satisfies ElementInstance;

const mockUserTaskElementInstance = {
  ...mockElementInstance,
  type: 'USER_TASK',
  elementName: 'User Task',
} satisfies ElementInstance;

const baseJobFields = {
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  processDefinitionId: 'process-def-1',
  elementId: 'Task_1',
  elementInstanceKey: '123456789',
  worker: 'worker-1',
  retries: 3,
  deadline: null,
  customHeaders: null,
  tenantId: '<default>',
  isDenied: false,
  deniedReason: null,
  hasFailedWithRetriesLeft: false,
  errorCode: null,
  errorMessage: null,
  endTime: '2023-01-15T10:05:00.000Z',
  rootProcessInstanceKey: null,
  creationTime: null,
  lastUpdateTime: null,
  tags: [],
} satisfies Omit<
  Job,
  'jobKey' | 'type' | 'state' | 'kind' | 'listenerEventType'
>;

const mockExecutionListenerJob = {
  ...baseJobFields,
  jobKey: '100000001',
  type: 'io.camunda:execution-listener',
  state: 'COMPLETED',
  kind: 'EXECUTION_LISTENER',
  listenerEventType: 'START',
} satisfies Job;

const mockTaskListenerJob = {
  ...baseJobFields,
  jobKey: '100000002',
  type: 'io.camunda:task-listener',
  state: 'COMPLETED',
  kind: 'TASK_LISTENER',
  listenerEventType: 'COMPLETING',
} satisfies Job;

function getWrapper(initialSearchParams?: string) {
  const path = Paths.processInstanceListeners({
    processInstanceId: PROCESS_INSTANCE_ID,
  });
  const initialEntry = initialSearchParams
    ? `${path}?${initialSearchParams}`
    : path;

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    const router = createMemoryRouter(
      [
        {
          path: Paths.processInstance(undefined, true),
          children: [
            {
              path: Paths.processInstanceListeners({isRelative: true}),
              element: children,
            },
          ],
        },
      ],
      {
        initialEntries: [initialEntry],
      },
    );

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<ListenersTab />', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);
  });

  it('should filter job searches by listener kind', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockValidatedSearchJobs(expectedPayload, searchResult([]));

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText('This element has no execution listeners'),
    ).toBeInTheDocument();
  });

  it('should show empty message when no listeners exist', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockValidatedSearchJobs(expectedPayload, searchResult([]));

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText('This element has no execution listeners'),
    ).toBeInTheDocument();
  });

  it('should display execution listener jobs', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);
    mockValidatedSearchJobs(
      expectedPayload,
      searchResult([mockExecutionListenerJob]),
    );

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Execution listener')).toBeInTheDocument();
    expect(screen.getByText('100000001')).toBeInTheDocument();
    expect(
      screen.getByText('io.camunda:execution-listener'),
    ).toBeInTheDocument();
  });

  it('should display task listener jobs', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockFetchElementInstance('123456789').withSuccess(
      mockUserTaskElementInstance,
    );
    mockValidatedSearchJobs(
      expectedPayload,
      searchResult([mockTaskListenerJob]),
    );

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Task listener')).toBeInTheDocument();
    expect(screen.getByText('100000002')).toBeInTheDocument();
    expect(screen.getByText('io.camunda:task-listener')).toBeInTheDocument();
  });

  it('should display both listener types', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockFetchElementInstance('123456789').withSuccess(
      mockUserTaskElementInstance,
    );
    mockValidatedSearchJobs(
      expectedPayload,
      searchResult([mockExecutionListenerJob, mockTaskListenerJob]),
    );

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Execution listener')).toBeInTheDocument();
    expect(screen.getByText('Task listener')).toBeInTheDocument();
  });

  it('should show empty message for user task with no listeners', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockFetchElementInstance('123456789').withSuccess(
      mockUserTaskElementInstance,
    );
    mockValidatedSearchJobs(expectedPayload, searchResult([]));

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText(
        'This element has no execution listeners nor user task listeners',
      ),
    ).toBeInTheDocument();
  });

  it('should show listener type dropdown for user tasks', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockFetchElementInstance('123456789').withSuccess(
      mockUserTaskElementInstance,
    );
    mockValidatedSearchJobs(
      expectedPayload,
      searchResult([mockExecutionListenerJob, mockTaskListenerJob]),
    );

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByTestId('listener-type-filter'),
    ).toBeInTheDocument();
  });

  it('should not show listener type dropdown for non-user-task elements', async () => {
    const expectedPayload = buildExpectedPayload(
      z.strictObject({
        $in: z.tuple([
          z.literal('EXECUTION_LISTENER'),
          z.literal('TASK_LISTENER'),
        ]),
      }),
    );

    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);
    mockValidatedSearchJobs(
      expectedPayload,
      searchResult([mockExecutionListenerJob]),
    );

    render(<ListenersTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Execution listener')).toBeInTheDocument();
    expect(
      screen.queryByTestId('listener-type-filter'),
    ).not.toBeInTheDocument();
  });
});
