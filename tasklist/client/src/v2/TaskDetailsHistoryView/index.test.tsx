/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {Component, loader, ErrorBoundary} from './index';
import {render, screen} from 'common/testing/testing-library';
import {http, HttpResponse} from 'msw';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import * as auditLogMocks from 'v2/mocks/auditLogs';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.9';

function getRouter(initialEntry: string = '/0/history') {
  const routes = [
    {
      path: ':id/history',
      element: <Component />,
      loader: loader,
      ErrorBoundary,
      HydrateFallback: () => null,
    },
  ];

  return createMemoryRouter(routes, {
    initialEntries: [initialEntry],
  });
}

const mockClient = getMockQueryClient();
const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={mockClient}>{children}</QueryClientProvider>
  );
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

    render(<RouterProvider router={getRouter()} />, {wrapper: Wrapper});

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

    render(<RouterProvider router={getRouter()} />, {wrapper: Wrapper});

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
      wrapper: Wrapper,
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

    expect(screen.getByText(/CREATE/)).toBeInTheDocument();
  });

  it('should load and display audit logs', async () => {
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

    render(<RouterProvider router={getRouter()} />, {wrapper: Wrapper});

    expect(
      await screen.findByTestId('task-details-history-view'),
    ).toBeInTheDocument();

    expect(screen.getByText(/CREATE/)).toBeInTheDocument();
  });
});
