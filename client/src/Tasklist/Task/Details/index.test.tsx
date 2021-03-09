/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Details} from './';

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {Route, MemoryRouter} from 'react-router-dom';
import {
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
} from 'modules/queries/get-task';
import {
  mockGetAllOpenTasks,
  mockGetAllOpenTasksUnclaimed,
} from 'modules/queries/get-tasks';
import {mockClaimTask} from 'modules/mutations/claim-task';
import {mockUnclaimTask} from 'modules/mutations/unclaim-task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MockedResponse} from '@apollo/client/testing';

type GetWrapperProps = {
  id: string;
  mocks: MockedResponse[];
};

const getWrapper = ({id, mocks}: GetWrapperProps) => {
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={[`/${id}`]}>
      <Route path="/:id">
        <MockedApolloProvider mocks={mocks}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </MockedApolloProvider>
      </Route>
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Details />', () => {
  it('should render completed task details', async () => {
    render(<Details />, {
      wrapper: getWrapper({id: '0', mocks: [mockGetTaskCompleted]}),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Workflow')).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Demo User',
    );
    expect(
      screen.queryByRole('button', {name: 'Unclaim'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/2020-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();
  });

  it('should render unclaimed task details', async () => {
    render(<Details />, {
      wrapper: getWrapper({id: '0', mocks: [mockGetTaskUnclaimed]}),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Workflow')).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent('--');
    expect(screen.getByRole('button', {name: 'Claim'})).toBeInTheDocument();
    expect(
      screen.getByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(screen.queryByText('Completion Time')).not.toBeInTheDocument();
    expect(
      screen.getByText('Claim the Task to start working on it'),
    ).toBeInTheDocument();
  });

  it('should render unclaimed task and claim it', async () => {
    render(<Details />, {
      wrapper: getWrapper({
        id: '0',
        mocks: [mockGetTaskUnclaimed, mockClaimTask, mockGetAllOpenTasks],
      }),
    });
    expect(
      await screen.findByRole('button', {name: 'Claim'}),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Claim the Task to start working on it'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Claim'}));

    expect(
      await screen.findByRole('button', {name: 'Unclaim'}),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: 'Claim'}),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Demo User',
    );
    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();
  });

  it('should render claimed task and unclaim it', async () => {
    render(<Details />, {
      wrapper: getWrapper({
        id: '0',
        mocks: [
          mockGetTaskClaimed,
          mockUnclaimTask,
          mockGetAllOpenTasksUnclaimed,
        ],
      }),
    });

    expect(
      await screen.findByRole('button', {name: 'Unclaim'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Unclaim'}));

    expect(
      await screen.findByRole('button', {name: 'Claim'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Unclaim'}),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent('--');
    expect(
      screen.getByText('Claim the Task to start working on it'),
    ).toBeInTheDocument();
  });
});
