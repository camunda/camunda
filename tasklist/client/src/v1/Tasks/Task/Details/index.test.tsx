/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse, http} from 'msw';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'common/testing/testing-library';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import * as userMocks from 'common/mocks/current-user';
import * as taskMocks from 'v1/mocks/task';
import * as processMocks from 'v1/mocks/processes';
import {Component} from '.';

const getWrapper = (id: string = '0') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MemoryRouter initialEntries={[`/${id}`]}>
        <Routes>
          <Route path="/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('Task Details', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => HttpResponse.json(userMocks.currentUser),
        {once: true},
      ),
    );
  });

  it('shows task and process tabs when a process definition is returned', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => HttpResponse.json(taskMocks.assignedTask()),
        {once: true},
      ),
      http.get(
        '/v1/internal/processes/:process',
        () => HttpResponse.json(processMocks.processWithBpmnModel),
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTitle(/task details header/i)).toBeVisible();
    const nav = screen.getByLabelText(/task details navigation/i);
    expect(nav).toBeVisible();
    expect(within(nav).getByLabelText(/show task/i)).toBeVisible();
    expect(
      within(nav).getByLabelText(/show associated BPMN process/i),
    ).toBeVisible();
  });

  it('does not show process tab when no process definition is returned', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => HttpResponse.json(taskMocks.assignedTask()),
        {once: true},
      ),
      http.get(
        '/v1/internal/processes/:process',
        () => HttpResponse.json(processMocks.process),
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTitle(/task details header/i)).toBeVisible();
    const nav = screen.getByLabelText(/task details navigation/i);
    expect(nav).toBeVisible();
    expect(within(nav).getByLabelText(/show task/i)).toBeVisible();
    expect(
      within(nav).getByLabelText(/show associated BPMN process/i),
    ).not.toBeVisible();
  });

  it('does not show process tab when the process definition returns a not found response', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => HttpResponse.json(taskMocks.assignedTask()),
        {once: true},
      ),
      http.get(
        '/v1/internal/processes/:process',
        () => new HttpResponse(null, {status: 404}),
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTitle(/task details header/i)).toBeVisible();
    const nav = screen.getByLabelText(/task details navigation/i);
    expect(nav).toBeVisible();
    expect(within(nav).getByLabelText(/show task/i)).toBeVisible();
    expect(
      within(nav).getByLabelText(/show associated BPMN process/i),
    ).not.toBeVisible();
  });

  it('does not show process tab when the process definition returns a forbidden response', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => HttpResponse.json(taskMocks.assignedTask()),
        {once: true},
      ),
      http.get(
        '/v1/internal/processes/:process',
        () => new HttpResponse(null, {status: 403}),
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTitle(/task details header/i)).toBeVisible();
    const nav = screen.getByLabelText(/task details navigation/i);
    expect(nav).toBeVisible();
    expect(within(nav).getByLabelText(/show task/i)).toBeVisible();
    expect(
      within(nav).getByLabelText(/show associated BPMN process/i),
    ).not.toBeVisible();
  });

  it('does not show process tab when the task is completed', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => HttpResponse.json(taskMocks.completedTask()),
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTitle(/task details header/i)).toBeVisible();
    const nav = screen.getByLabelText(/task details navigation/i);
    expect(nav).toBeVisible();
    expect(within(nav).getByLabelText(/show task/i)).toBeVisible();
    expect(
      within(nav).getByLabelText(/show associated BPMN process/i),
    ).not.toBeVisible();
  });
});
