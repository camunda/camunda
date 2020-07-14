/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from './index';

import * as React from 'react';
import {Route, Router} from 'react-router-dom';
import {createMemoryHistory, History} from 'history';
import {render, screen, fireEvent} from '@testing-library/react';
import {MockedResponse} from '@apollo/react-testing';

import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockGetTaskCreated,
  mockGetTaskCompleted,
} from 'modules/queries/get-task';
import {
  mockTaskWithVariables,
  mockTaskWithoutVariables,
} from 'modules/queries/get-task-variables';
import {
  mockGetTaskClaimed,
  mockGetTaskUnclaimed,
} from 'modules/queries/get-task-details';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {
  mockCompleteTask,
  mockCompleteTaskWithVariable,
} from 'modules/mutations/complete-task';

type GetWrapperProps = {
  id: string;
  mocks: MockedResponse[];
  history?: History;
};

const getWrapper = ({
  id,
  mocks,
  history = createMemoryHistory(),
}: GetWrapperProps) => {
  history.push(`/${id}`);

  const Wrapper: React.FC = ({children}) => (
    <Router history={history}>
      <Route path="/:id">
        <MockedApolloProvider mocks={mocks}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </MockedApolloProvider>
      </Route>
    </Router>
  );

  return Wrapper;
};

describe('<Task />', () => {
  it('should render created task', async () => {
    render(<Task />, {
      wrapper: getWrapper({
        id: '0',
        mocks: [
          mockGetTaskCreated,
          mockGetTaskClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
        ],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: 'Complete Task'}),
    ).toBeInTheDocument();
  });

  it('should render completed task', async () => {
    render(<Task />, {
      wrapper: getWrapper({
        id: '0',
        mocks: [
          mockGetTaskCompleted,
          mockGetTaskUnclaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
        ],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Complete Task'}),
    ).not.toBeInTheDocument();
  });

  it('should complete task without variables', async () => {
    const history = createMemoryHistory();

    render(<Task />, {
      wrapper: getWrapper({
        id: '0',
        mocks: [
          mockGetTaskCreated,
          mockGetTaskClaimed,
          mockTaskWithoutVariables,
          mockGetCurrentUser,
          mockCompleteTask,
        ],
        history,
      }),
    });

    fireEvent.click(await screen.findByRole('button', {name: 'Complete Task'}));

    expect(
      await screen.findByRole('button', {name: 'Complete Task'}),
    ).not.toBeInTheDocument();
    expect(history.location.pathname).toBe('/');
  });

  it('should change variable and complete task', async () => {
    const history = createMemoryHistory();

    render(<Task />, {
      wrapper: getWrapper({
        id: '0',
        mocks: [
          mockGetTaskCreated,
          mockGetTaskClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
          mockCompleteTaskWithVariable,
        ],
        history,
      }),
    });

    fireEvent.change(await screen.findByRole('textbox', {name: 'myVar'}), {
      target: {value: 'newValue'},
    });

    fireEvent.click(await screen.findByRole('button', {name: 'Complete Task'}));

    expect(
      await screen.findByRole('button', {name: 'Complete Task'}),
    ).not.toBeInTheDocument();
    expect(history.location.pathname).toBe('/');
  });
});
