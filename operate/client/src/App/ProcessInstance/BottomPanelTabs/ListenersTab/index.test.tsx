/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within, waitFor} from 'modules/testing-library';
import {ListenersTab} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {searchResult} from 'modules/testUtils';
import type {Job} from '@camunda/camunda-api-zod-schemas/8.9';
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.9';

const PROCESS_INSTANCE_ID = mockProcessInstance.processInstanceKey;

const baseJob = {
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: mockProcessInstance.processDefinitionKey,
  processDefinitionId: mockProcessInstance.processDefinitionId,
  elementId: 'Task_1',
  elementInstanceKey: '100',
  worker: 'test-worker',
  retries: 3,
  deadline: '2024-01-01T00:00:00.000Z',
  customHeaders: {},
  tenantId: '<default>',
  isDenied: false,
  deniedReason: '',
  hasFailedWithRetriesLeft: false,
  errorCode: '',
  errorMessage: '',
  rootProcessInstanceKey: null,
  creationTime: null,
  lastUpdateTime: null,
} as const;

const globalExecutionListener = {
  ...baseJob,
  jobKey: '1001',
  type: 'globalExecType',
  state: 'COMPLETED',
  kind: 'EXECUTION_LISTENER',
  listenerEventType: 'START',
  endTime: '2024-01-01T10:00:00.000Z',
  tags: ['GLOBAL_LISTENER'],
} satisfies Job;

const modelExecutionListener = {
  ...baseJob,
  jobKey: '1002',
  type: 'modelExecType',
  state: 'COMPLETED',
  kind: 'EXECUTION_LISTENER',
  listenerEventType: 'END',
  endTime: '2024-01-01T11:00:00.000Z',
  tags: [],
} satisfies Job;

const globalTaskListener = {
  ...baseJob,
  jobKey: '1003',
  type: 'globalTaskType',
  state: 'FAILED',
  kind: 'TASK_LISTENER',
  listenerEventType: 'COMPLETING',
  endTime: '2024-01-01T12:00:00.000Z',
  tags: ['GLOBAL_LISTENER'],
} satisfies Job;

const modelTaskListener = {
  ...baseJob,
  jobKey: '1004',
  type: 'modelTaskType',
  state: 'COMPLETED',
  kind: 'TASK_LISTENER',
  listenerEventType: 'ASSIGNING',
  endTime: '2024-01-01T13:00:00.000Z',
  tags: [],
} satisfies Job;

function getWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter
        initialEntries={[Paths.processInstance(PROCESS_INSTANCE_ID)]}
      >
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
}

describe('ListenersTab', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchProcessInstances().withSuccess(
      searchResult([mockProcessInstance]),
    );
  });

  it('should render Source column with correct Global and Model tags', async () => {
    mockSearchJobs().withSuccess(
      searchResult([
        globalExecutionListener,
        modelExecutionListener,
        globalTaskListener,
        modelTaskListener,
      ]),
    );

    render(<ListenersTab />, {wrapper: getWrapper()});

    const list = await screen.findByTestId('listeners-list');
    const listScope = within(list);

    // Verify Source column header exists
    expect(listScope.getByText('Source')).toBeInTheDocument();

    // Verify Global tags rendered for global listeners
    const globalTags = listScope.getAllByText('Global');
    expect(globalTags).toHaveLength(2);

    // Verify Model tags rendered for model listeners
    const modelTags = listScope.getAllByText('Model');
    expect(modelTags).toHaveLength(2);
  });

  it('should filter by Global source when source filter is selected', async () => {
    mockSearchJobs().withSuccess(
      searchResult([
        globalExecutionListener,
        modelExecutionListener,
        globalTaskListener,
        modelTaskListener,
      ]),
    );

    const {user} = render(<ListenersTab />, {wrapper: getWrapper()});

    const list = await screen.findByTestId('listeners-list');

    // All four listeners visible initially
    expect(within(list).getAllByText('Global')).toHaveLength(2);
    expect(within(list).getAllByText('Model')).toHaveLength(2);

    // Select "Global" from source filter dropdown
    await user.click(
      screen.getByRole('combobox', {name: /source/i}),
    );
    await user.click(screen.getByRole('option', {name: 'Global'}));

    // Only global listeners should remain
    await waitFor(() => {
      expect(within(list).getAllByText('Global')).toHaveLength(2);
    });
    expect(within(list).queryAllByText('Model')).toHaveLength(0);
  });

  it('should filter by Model source when source filter is selected', async () => {
    mockSearchJobs().withSuccess(
      searchResult([
        globalExecutionListener,
        modelExecutionListener,
        globalTaskListener,
        modelTaskListener,
      ]),
    );

    const {user} = render(<ListenersTab />, {wrapper: getWrapper()});

    const list = await screen.findByTestId('listeners-list');

    // Select "Model" from source filter dropdown
    await user.click(
      screen.getByRole('combobox', {name: /source/i}),
    );
    await user.click(screen.getByRole('option', {name: 'Model'}));

    // Only model listeners should remain
    await waitFor(() => {
      expect(within(list).getAllByText('Model')).toHaveLength(2);
    });
    expect(within(list).queryAllByText('Global')).toHaveLength(0);
  });

  it('should auto-fetch next page when source filter has no matches on first page', async () => {
    // Single persistent handler keyed by page.from to avoid {once: true} nondeterminism
    mockServer.use(
      http.post(endpoints.queryJobs.getUrl(), async ({request}) => {
        const body = (await request.json()) as {page?: {from?: number}};
        if (!body.page?.from) {
          // Page 1: only model listeners (totalItems=100 so hasNextPage=true)
          return HttpResponse.json(
            searchResult([modelExecutionListener, modelTaskListener], 100),
          );
        }
        // Page 2+: contains a global listener
        return HttpResponse.json(
          searchResult([globalExecutionListener]),
        );
      }),
    );

    const {user} = render(<ListenersTab />, {wrapper: getWrapper()});

    // Wait for first page to render
    const list = await screen.findByTestId('listeners-list');
    expect(within(list).getAllByText('Model')).toHaveLength(2);

    // Select "Global" from source filter — no matches on page 1
    await user.click(
      screen.getByRole('combobox', {name: /source/i}),
    );
    await user.click(screen.getByRole('option', {name: 'Global'}));

    // The useEffect should auto-fetch page 2, which has a global listener
    await waitFor(() => {
      expect(
        screen.getByTestId(globalExecutionListener.jobKey),
      ).toBeInTheDocument();
    });
  });

  it('should show empty message after all pages exhausted with no source matches', async () => {
    // Single page with only model listeners (totalItems matches length → no more pages)
    mockSearchJobs().withSuccess(
      searchResult([modelExecutionListener, modelTaskListener]),
    );

    const {user} = render(<ListenersTab />, {wrapper: getWrapper()});

    const list = await screen.findByTestId('listeners-list');
    expect(within(list).getAllByText('Model')).toHaveLength(2);

    // Select "Global" — no matches and no more pages
    await user.click(
      screen.getByRole('combobox', {name: /source/i}),
    );
    await user.click(screen.getByRole('option', {name: 'Global'}));

    // Should show empty message since all pages exhausted with no global matches
    expect(
      await screen.findByText(
        'No global listeners match the selected filter',
      ),
    ).toBeInTheDocument();
  });
});
