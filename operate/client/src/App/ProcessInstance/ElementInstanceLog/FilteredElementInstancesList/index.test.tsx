/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {waitFor} from '@testing-library/react';
import {act} from 'react';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {ErrorBoundary} from 'react-error-boundary';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {
  endpoints,
  type ElementInstance,
  type QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {FilteredElementInstancesList} from './index';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {Paths} from 'modules/Routes';
import {getForbiddenPermissionsError} from 'modules/constants/permissions';

const INSTANCE_HISTORY_FORBIDDEN = getForbiddenPermissionsError(
  'Instance History',
  'this instance history',
);

const PROCESS_INSTANCE_KEY = '1';

const createMockElementInstance = (
  overrides: Partial<ElementInstance> = {},
): ElementInstance => ({
  elementInstanceKey: '100',
  elementId: 'order_task',
  elementName: 'Order Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2023-01-01T10:00:00.000Z',
  processDefinitionKey: '2',
  processDefinitionId: 'test-process',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  hasIncident: false,
  tenantId: '<default>',
  endDate: null,
  rootProcessInstanceKey: null,
  incidentKey: null,
  ...overrides,
});

const mockResponse = (
  items: ElementInstance[],
  totalItems: number,
): QueryElementInstancesResponseBody => ({
  items,
  page: {
    totalItems,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
});

const businessObjects: BusinessObjects = {};

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <MemoryRouter initialEntries={[Paths.processInstance(PROCESS_INSTANCE_KEY)]}>
    <ProcessDefinitionKeyContext.Provider value="2">
      <QueryClientProvider client={getMockQueryClient()}>
        <Routes>
          <Route
            path={Paths.processInstance()}
            element={
              <ErrorBoundary
                fallbackRender={() => (
                  <>
                    <p>{INSTANCE_HISTORY_FORBIDDEN.message}</p>
                    {INSTANCE_HISTORY_FORBIDDEN.additionalInfo && (
                      <p>{INSTANCE_HISTORY_FORBIDDEN.additionalInfo}</p>
                    )}
                  </>
                )}
              >
                {children}
              </ErrorBoundary>
            }
          />
        </Routes>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  </MemoryRouter>
);

const hasInertAncestor = (element: HTMLElement) =>
  element.closest('[inert]') !== null;

describe('<FilteredElementInstancesList />', () => {
  it('renders results returned by the search API', async () => {
    mockSearchElementInstances().withSuccess(
      mockResponse(
        [
          createMockElementInstance({
            elementInstanceKey: '100',
            elementName: 'Order Task',
            elementId: 'order_task',
          }),
          createMockElementInstance({
            elementInstanceKey: '101',
            elementName: 'Validate Order',
            elementId: 'validate_order',
          }),
        ],
        2,
      ),
    );

    render(
      <FilteredElementInstancesList
        searchText="order"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(await screen.findByTestId('search-result-100')).toBeInTheDocument();
    expect(screen.getByTestId('search-result-101')).toBeInTheDocument();
  });

  it('gives each treeitem row a role="tree" ancestor', async () => {
    mockSearchElementInstances().withSuccess(
      mockResponse(
        [
          createMockElementInstance({
            elementInstanceKey: '100',
            elementName: 'Order Task',
            elementId: 'order_task',
          }),
        ],
        1,
      ),
    );

    render(
      <FilteredElementInstancesList
        searchText="order"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    const row = await screen.findByTestId('search-result-100');
    expect(row).toHaveAttribute('role', 'treeitem');
    expect(row.closest('[role="tree"]')).not.toBeNull();
  });

  it('renders the empty state when there are no results', async () => {
    mockSearchElementInstances().withSuccess(mockResponse([], 0));

    render(
      <FilteredElementInstancesList
        searchText="zzz"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('No matching elements')).toBeInTheDocument();
    expect(screen.getByText('Try a different name or ID')).toBeInTheDocument();
  });

  it('does not blank the results-count live region while a background poll is in flight', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(
      mockResponse([createMockElementInstance({elementInstanceKey: '100'})], 2),
    );

    render(
      <FilteredElementInstancesList
        searchText="order"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        '2 matching elements',
      );
    });

    mockSearchElementInstances().withDelay(
      mockResponse([createMockElementInstance({elementInstanceKey: '100'})], 3),
    );

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(screen.getByRole('status')).toHaveTextContent('2 matching elements');

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        '3 matching elements',
      );
    });

    vi.useRealTimers();
  });

  it('dims the results while a new status filter is loading its own data', async () => {
    mockSearchElementInstances().withSuccess(
      mockResponse([createMockElementInstance({elementInstanceKey: '100'})], 1),
    );

    const {rerender} = render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="active"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        '1 matching elements',
      );
    });

    expect(screen.getByTestId('filtered-results')).toHaveStyle({
      opacity: '1',
    });

    mockSearchElementInstances().withDelay(
      mockResponse([createMockElementInstance({elementInstanceKey: '200'})], 1),
    );

    rerender(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="incidents"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
    );

    expect(screen.getByTestId('filtered-results')).toHaveStyle({
      opacity: '0.5',
    });
    expect(screen.getByTestId('search-result-100')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId('filtered-results')).toHaveStyle({
        opacity: '1',
      });
    });
    expect(screen.getByTestId('search-result-200')).toBeInTheDocument();
  });

  it('makes stale results non-interactive while a new status filter loads', async () => {
    mockSearchElementInstances().withSuccess(
      mockResponse([createMockElementInstance({elementInstanceKey: '100'})], 1),
    );

    const {rerender} = render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="active"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(screen.getByTestId('search-result-100')).toBeInTheDocument();
    });
    expect(screen.getByTestId('filtered-results').hasAttribute('inert')).toBe(
      false,
    );

    mockSearchElementInstances().withDelay(
      mockResponse([createMockElementInstance({elementInstanceKey: '200'})], 1),
    );

    rerender(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="incidents"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
    );

    expect(screen.getByTestId('filtered-results')).toHaveAttribute('inert');

    await waitFor(() => {
      expect(screen.getByTestId('search-result-200')).toBeInTheDocument();
    });
    expect(screen.getByTestId('filtered-results').hasAttribute('inert')).toBe(
      false,
    );
  });

  it('keeps announcing the result count while stale results are inert', async () => {
    mockSearchElementInstances().withSuccess(
      mockResponse([createMockElementInstance({elementInstanceKey: '100'})], 1),
    );

    const {rerender} = render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="active"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        '1 matching elements',
      );
    });

    mockSearchElementInstances().withDelay(
      mockResponse(
        [
          createMockElementInstance({elementInstanceKey: '200'}),
          createMockElementInstance({elementInstanceKey: '201'}),
        ],
        2,
      ),
    );

    rerender(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="incidents"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
    );

    expect(screen.getByTestId('filtered-results')).toHaveAttribute('inert');
    expect(hasInertAncestor(screen.getByRole('status'))).toBe(false);

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(
        '2 matching elements',
      );
    });
    expect(hasInertAncestor(screen.getByRole('status'))).toBe(false);
  });

  it('renders an error message on a non-permissions error', async () => {
    mockSearchElementInstances().withServerError(500);

    render(
      <FilteredElementInstancesList
        searchText="order"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByText('Search results could not be fetched'),
    ).toBeInTheDocument();
  });

  it('renders the permissions error UX on a 403', async () => {
    mockSearchElementInstances().withServerError(403);

    render(
      <FilteredElementInstancesList
        searchText="order"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByText(INSTANCE_HISTORY_FORBIDDEN.message),
    ).toBeInTheDocument();
  });

  it('excludes elements with incidents from the request filter when statusFilter is "active"', async () => {
    let capturedFilter: unknown;
    mockServer.use(
      http.post(
        endpoints.queryElementInstances.getUrl(),
        async ({request}) => {
          const body = (await request.json()) as {
            filter?: {state?: unknown; hasIncident?: unknown};
          };
          capturedFilter = body.filter;
          return HttpResponse.json(mockResponse([], 0));
        },
        {once: true},
      ),
    );

    render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="active"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(capturedFilter).toMatchObject({
        state: 'ACTIVE',
        hasIncident: false,
      });
    });
    expect(capturedFilter).not.toHaveProperty('$or');
  });

  it('adds hasIncident: true to the request filter when statusFilter is "incidents"', async () => {
    let capturedFilter: unknown;
    mockServer.use(
      http.post(
        endpoints.queryElementInstances.getUrl(),
        async ({request}) => {
          const body = (await request.json()) as {
            filter?: {hasIncident?: unknown};
          };
          capturedFilter = body.filter;
          return HttpResponse.json(mockResponse([], 0));
        },
        {once: true},
      ),
    );

    render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="incidents"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(capturedFilter).toMatchObject({hasIncident: true});
    });
  });

  it('combines search text and statusFilter with AND semantics', async () => {
    let capturedFilter: unknown;
    mockServer.use(
      http.post(
        endpoints.queryElementInstances.getUrl(),
        async ({request}) => {
          const body = (await request.json()) as {filter?: unknown};
          capturedFilter = body.filter;
          return HttpResponse.json(mockResponse([], 0));
        },
        {once: true},
      ),
    );

    render(
      <FilteredElementInstancesList
        searchText="order"
        statusFilter="active"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    await waitFor(() => {
      expect(capturedFilter).toMatchObject({
        state: 'ACTIVE',
        hasIncident: false,
      });
    });
    expect(capturedFilter).toHaveProperty('$or');
  });

  it('renders status-specific empty state copy for the Active filter with no search text', async () => {
    mockSearchElementInstances().withSuccess(mockResponse([], 0));

    render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="active"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('No active elements')).toBeInTheDocument();
    expect(
      screen.queryByText('Try a different name or ID'),
    ).not.toBeInTheDocument();
  });

  it('renders status-specific empty state copy for the Incidents filter with no search text', async () => {
    mockSearchElementInstances().withSuccess(mockResponse([], 0));

    render(
      <FilteredElementInstancesList
        searchText=""
        statusFilter="incidents"
        processInstanceKey={PROCESS_INSTANCE_KEY}
        businessObjects={businessObjects}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByText('No elements with incidents'),
    ).toBeInTheDocument();
  });
});
