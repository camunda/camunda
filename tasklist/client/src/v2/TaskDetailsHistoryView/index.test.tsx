/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createMemoryRouter, Outlet, RouterProvider} from 'react-router-dom';
import {Component, loader, ErrorBoundary} from './index';
import {
  Component as AuditLogDetailsComponent,
  loader as auditLogDetailsLoader,
} from './HistoryItemDetailsModal';
import {render, screen, within} from 'common/testing/testing-library';
import {http, HttpResponse} from 'msw';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {LocationLog} from 'common/testing/LocationLog';
import * as auditLogMocks from 'v2/mocks/auditLogs';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.9';

function getRouter(initialEntry: string = '/0/history') {
  const routes = [
    {
      path: '/',
      element: (
        <>
          <Outlet />
          <LocationLog />
        </>
      ),
      children: [
        {
          path: ':id/history',
          element: <Component />,
          loader: loader,
          ErrorBoundary,
          HydrateFallback: () => null,
          children: [
            {
              path: ':auditLogKey',
              element: <AuditLogDetailsComponent />,
              loader: auditLogDetailsLoader,
              HydrateFallback: () => null,
            },
          ],
        },
      ],
    },
  ];

  return createMemoryRouter(routes, {
    initialEntries: [initialEntry],
  });
}

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>{children}</QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<TaskDetailsHistoryView />', () => {
  it('should display error boundary when audit logs fail to load', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () => HttpResponse.error(),
      ),
    );

    render(<RouterProvider router={getRouter()} />, {wrapper: getWrapper()});

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();

    expect(
      screen.getByText(/could not load the task history/i),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /try again/i}),
    ).toBeInTheDocument();
  });

  it('should display forbidden error when audit logs return 403', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () => new HttpResponse(null, {status: 403}),
      ),
    );

    render(<RouterProvider router={getRouter()} />, {wrapper: getWrapper()});

    expect(
      await screen.findByText(/don't have permission to view task history/i),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: /learn more about roles and permissions/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /try again/i}),
    ).not.toBeInTheDocument();
  });

  it('should retry loading audit logs when clicking try again', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () => HttpResponse.error(),
        {once: true},
      ),
    );

    const {user} = render(<RouterProvider router={getRouter()} />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();

    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock(),
          ),
      ),
    );

    await user.click(screen.getByRole('button', {name: /try again/i}));

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    expect(screen.getByText('Create User task')).toBeInTheDocument();
  });

  it('should load and display audit logs in a table', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock(),
          ),
      ),
    );

    render(<RouterProvider router={getRouter()} />, {wrapper: getWrapper()});

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    expect(screen.getByText('Operation')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Actor')).toBeInTheDocument();
    expect(screen.getByText('Time')).toBeInTheDocument();

    expect(screen.getByText('Create User task')).toBeInTheDocument();
    expect(screen.getByText('Assign User task')).toBeInTheDocument();

    expect(screen.getAllByTestId('success-icon')).toHaveLength(2);
    expect(screen.getAllByText('Success')).toHaveLength(2);

    expect(screen.getAllByText('demo')).toHaveLength(2);
  });

  it('should display empty message when no audit logs exist', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock([], 0),
          ),
      ),
    );

    render(<RouterProvider router={getRouter()} />, {wrapper: getWrapper()});

    expect(
      await screen.findByText('No history entries found for this task'),
    ).toBeInTheDocument();
  });

  it('should display fail status icon for failed operations', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock([
              auditLogMocks.auditLog({result: 'FAIL'}),
            ]),
          ),
      ),
    );

    render(<RouterProvider router={getRouter()} />, {wrapper: getWrapper()});

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    expect(screen.getByTestId('fail-icon')).toBeInTheDocument();
    expect(screen.getByText('Fail')).toBeInTheDocument();
  });

  it('should display details button for each row', async () => {
    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock(),
          ),
      ),
    );

    render(<RouterProvider router={getRouter()} />, {wrapper: getWrapper()});

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    expect(
      screen.getAllByRole('button', {
        name: /open details/i,
      }),
    ).toHaveLength(2);
  });

  it('should display modal when navigating directly to audit log details route', async () => {
    const testAuditLog = auditLogMocks.auditLog({
      auditLogKey: 'direct-nav-audit-log-key',
      operationType: 'ASSIGN',
      entityType: 'USER_TASK',
      result: 'SUCCESS',
      actorId: 'directuser',
      timestamp: '2024-02-20T14:00:00.000Z',
    });

    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock([testAuditLog]),
          ),
      ),
      http.get('/v2/audit-logs/:auditLogKey', () =>
        HttpResponse.json(testAuditLog),
      ),
    );

    render(
      <RouterProvider
        router={getRouter('/0/history/direct-nav-audit-log-key')}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('heading', {name: /assign user task/i}),
    ).toBeInTheDocument();

    expect(
      within(screen.getByRole('dialog')).getByText('directuser'),
    ).toBeInTheDocument();

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/0/history/direct-nav-audit-log-key',
    );
  });

  it('should open details modal when clicking details button', async () => {
    const testAuditLog = auditLogMocks.auditLog({
      auditLogKey: 'test-audit-log-key',
      operationType: 'CREATE',
      entityType: 'USER_TASK',
      result: 'SUCCESS',
      actorId: 'testuser',
      timestamp: '2024-01-15T10:30:00.000Z',
    });

    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock([testAuditLog]),
          ),
      ),
      http.get('/v2/audit-logs/:auditLogKey', () =>
        HttpResponse.json(testAuditLog),
      ),
    );

    const {user} = render(<RouterProvider router={getRouter()} />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /open details/i}));

    expect(
      await screen.findByRole('heading', {name: /create user task/i}),
    ).toBeInTheDocument();

    const modal = screen.getByRole('dialog');
    expect(within(modal).getByText('Status')).toBeInTheDocument();
    expect(within(modal).getByText('Actor')).toBeInTheDocument();
    expect(within(modal).getByText('Time')).toBeInTheDocument();
    expect(within(modal).getByText('testuser')).toBeInTheDocument();

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/0/history/test-audit-log-key',
    );
  });

  it('should close details modal when clicking close button', async () => {
    const testAuditLog = auditLogMocks.auditLog({
      auditLogKey: 'test-audit-log-key-2',
      operationType: 'CREATE',
    });

    nodeMockServer.use(
      http.post(
        endpoints.queryUserTaskAuditLogs.getUrl({
          userTaskKey: ':userTaskKey',
        }),
        () =>
          HttpResponse.json(
            auditLogMocks.getQueryUserTaskAuditLogsResponseMock([testAuditLog]),
          ),
      ),
      http.get('/v2/audit-logs/:auditLogKey', () =>
        HttpResponse.json(testAuditLog),
      ),
    );

    const {user} = render(<RouterProvider router={getRouter()} />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /open details/i}));

    expect(
      await screen.findByRole('heading', {name: /create user task/i}),
    ).toBeInTheDocument();

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/0/history/test-audit-log-key-2',
    );

    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: /close/i,
      }),
    );

    expect(
      screen.queryByRole('heading', {name: /create user task/i}),
    ).not.toBeInTheDocument();

    expect(screen.getByTestId('pathname')).toHaveTextContent('/0/history');
  });
});
