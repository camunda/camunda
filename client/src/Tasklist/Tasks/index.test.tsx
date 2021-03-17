/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';

import {Tasks} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
} from 'modules/queries/get-tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {MockedResponse} from '@apollo/client/testing';
import {FilterValues} from 'modules/constants/filterValues';

const getWrapper = (
  mock: MockedResponse[],
  history = createMemoryHistory({initialEntries: ['/']}),
) => {
  const Wrapper: React.FC = ({children}) => (
    <MockedApolloProvider mocks={mock}>
      <Router history={history}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </Router>
    </MockedApolloProvider>
  );

  return Wrapper;
};

describe('<Tasks />', () => {
  it('should not render when loading', async () => {
    render(<Tasks />, {wrapper: getWrapper([mockGetAllOpenTasks()])});

    expect(screen.queryByTestId('task-0')).not.toBeInTheDocument();
    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );
    expect(screen.getByTestId('task-0')).toBeInTheDocument();
  });

  it('should render tasks', async () => {
    render(<Tasks />, {wrapper: getWrapper([mockGetAllOpenTasks()])});

    const [firstTask, secondTask] = mockGetAllOpenTasks().result.data.tasks;

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    const withinFirstTask = within(screen.getByTestId('task-0'));
    const withinSecondTask = within(screen.getByTestId('task-1'));

    expect(withinFirstTask.getByText(firstTask.name)).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.workflowName),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.creationTime),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(
        `${firstTask.assignee?.firstname} ${firstTask.assignee?.lastname}`,
      ),
    ).toBeInTheDocument();

    expect(withinSecondTask.getByText(secondTask.name)).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.workflowName),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.creationTime),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(
        `${secondTask.assignee?.firstname} ${secondTask.assignee?.lastname}`,
      ),
    ).toBeInTheDocument();
  });

  it('should render empty message when there are no tasks', async () => {
    render(<Tasks />, {wrapper: getWrapper([mockGetEmptyTasks])});

    expect(
      await screen.findByText('There are no Tasks available'),
    ).toBeInTheDocument();
  });

  it('should show all tasks claimed by me', async () => {
    render(<Tasks />, {
      wrapper: getWrapper(
        [mockGetClaimedByMe, mockGetCurrentUser],
        createMemoryHistory({
          initialEntries: [`/?filter=${FilterValues.ClaimedByMe}`],
        }),
      ),
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
    expect(screen.getByTestId('task-2')).toBeInTheDocument();
  });

  it('should show all unclaimed tasks', async () => {
    render(<Tasks />, {
      wrapper: getWrapper(
        [mockGetUnclaimed],
        createMemoryHistory({
          initialEntries: [`/?filter=${FilterValues.Unclaimed}`],
        }),
      ),
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
    expect(screen.getByTestId('task-2')).toBeInTheDocument();
  });

  it('should show all completed tasks', async () => {
    render(<Tasks />, {
      wrapper: getWrapper(
        [mockGetCompleted],
        createMemoryHistory({
          initialEntries: [`/?filter=${FilterValues.Completed}`],
        }),
      ),
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
    expect(screen.getByTestId('task-2')).toBeInTheDocument();
  });

  it('should show the loading spinner while changing filters', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: [`/?filter=${FilterValues.Completed}`],
    });

    render(<Tasks />, {
      wrapper: getWrapper(
        [mockGetAllOpenTasks(), mockGetCompleted],
        historyMock,
      ),
    });

    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    historyMock.push('/');

    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );
  });
});
