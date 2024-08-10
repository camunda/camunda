/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {History} from './index';
import {MemoryRouter} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<History />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );
  });

  it('should fetch process instances', async () => {
    nodeMockServer.use(
      http.post(
        '/internal/users/:userId/process-instances',
        () => {
          return HttpResponse.json(processInstancesMocks.processInstances);
        },
        {once: true},
      ),
    );

    render(<History />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('history-skeleton'),
    );

    const [{process, id}] = processInstancesMocks.processInstances;

    expect(screen.getAllByText(process.bpmnProcessId)).toHaveLength(2);
    expect(screen.getAllByText(process.name!)).toHaveLength(2);
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(
      screen.getByText('01 Jan 2021 - 12:00 AM - Completed'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('completed-icon')).toBeInTheDocument();
    expect(screen.getByTestId('active-icon')).toBeInTheDocument();
    expect(screen.getByTestId('incident-icon')).toBeInTheDocument();
    expect(screen.getByTestId('terminated-icon')).toBeInTheDocument();
  });

  it('should show error message when fetching process instances fails', async () => {
    nodeMockServer.use(
      http.post(
        '/internal/users/:userId/process-instances',
        () => {
          return new HttpResponse(null, {status: 500});
        },
        {once: true},
      ),
    );

    render(<History />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('history-skeleton'),
    );

    expect(
      screen.getByText('Oops! Something went wrong while fetching the history'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Please check your internet connection and try again.'),
    ).toBeInTheDocument();
  });

  it('should show a message when no process instances are found', async () => {
    nodeMockServer.use(
      http.post(
        '/internal/users/:userId/process-instances',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
    );

    render(<History />, {
      wrapper: getWrapper(),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('history-skeleton'),
    );

    expect(screen.getByText('No history entries found')).toBeInTheDocument();
    expect(
      screen.getByText(
        'There is no history to display. Start a new process to see it here.',
      ),
    ).toBeInTheDocument();
  });
});
