/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from './index';

import * as React from 'react';
import {Route, Router} from 'react-router-dom';
import {createMemoryHistory, History} from 'history';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {MockedResponse} from '@apollo/client/testing';

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
  mockGetTaskDetailsClaimed,
  mockGetTaskDetailsUnclaimed,
} from 'modules/queries/get-task-details';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {
  mockCompleteTask,
  mockCompleteTaskWithAddedVariable,
  mockCompleteTaskWithEditedVariable,
} from 'modules/mutations/complete-task';
import {mockGetAllOpenTasks} from 'modules/queries/get-tasks';
import {mockClaimTask} from 'modules/mutations/claim-task';
import {mockUnclaimTask} from 'modules/mutations/unclaim-task';

type GetWrapperProps = {
  mocks: MockedResponse[];
  history: History;
};

const getWrapper = ({mocks, history}: GetWrapperProps) => {
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
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
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
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCompleted,
          mockGetTaskDetailsUnclaimed,
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
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithoutVariables('0'),
          mockGetCurrentUser,
          mockCompleteTask,
          mockGetAllOpenTasks,
        ],
      }),
    });

    fireEvent.click(await screen.findByRole('button', {name: 'Complete Task'}));

    await waitFor(() => {
      expect(history.location.pathname).toBe('/');
    });
  });

  it('should change variable and complete task', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
          mockCompleteTaskWithEditedVariable,
          mockGetAllOpenTasks,
        ],
      }),
    });

    fireEvent.change(await screen.findByRole('textbox', {name: 'myVar'}), {
      target: {value: '"newValue"'},
    });

    fireEvent.click(await screen.findByRole('button', {name: 'Complete Task'}));

    await waitFor(() => {
      expect(history.location.pathname).toBe('/');
    });
  });

  it('should add new variable and complete task', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
          mockCompleteTaskWithAddedVariable,
          mockGetAllOpenTasks,
        ],
      }),
    });

    fireEvent.click(await screen.findByText('Add Variable'));

    fireEvent.change(
      screen.getByRole('textbox', {name: 'new-variables[0].name'}),
      {
        target: {value: 'newVariableName'},
      },
    );

    fireEvent.change(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
      {
        target: {value: '"newVariableValue"'},
      },
    );

    fireEvent.click(await screen.findByRole('button', {name: 'Complete Task'}));

    await waitFor(() => {
      expect(history.location.pathname).toBe('/');
    });
  });

  it('should disable submit button on form errors for existing variables', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
        ],
      }),
    });

    fireEvent.change(await screen.findByRole('textbox', {name: 'myVar'}), {
      target: {value: '{{ invalid value'},
    });

    expect(screen.getAllByTestId(/^warning-icon/)).toHaveLength(1);
    expect(screen.getByTestId('warning-icon-myVar')).toBeInTheDocument();
    expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Complete Task'})).toBeDisabled();
  });

  it('should disable submit button on form errors for new variables', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
        ],
      }),
    });

    fireEvent.click(await screen.findByText('Add Variable'));
    fireEvent.change(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
      {
        target: {value: '{{ invalid value'},
      },
    );

    expect(screen.getAllByTestId(/^warning-icon/)).toHaveLength(1);
    expect(
      screen.getByTestId('warning-icon-new-variables[0].value'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Complete Task'})).toBeDisabled();
  });

  it('should show a loading spinner while loading', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithVariables,
          mockGetCurrentUser,
        ],
      }),
    });

    expect(screen.getByTestId('details-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('details-overlay'));
  });

  it('should reset variables on unclaim/claim', async () => {
    render(<Task />, {
      wrapper: getWrapper({
        history: createMemoryHistory({
          initialEntries: ['/0'],
        }),
        mocks: [
          mockGetCurrentUser,
          mockGetTaskCreated,
          mockGetTaskDetailsClaimed,
          mockTaskWithoutVariables('0'),
          mockUnclaimTask,
          mockGetAllOpenTasks,
          mockClaimTask,
          mockGetAllOpenTasks,
        ],
      }),
    });

    fireEvent.click(await screen.findByRole('button', {name: /Add Variable/}));

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].name'}),
      {target: {value: 'valid_name'}},
    );

    fireEvent.change(
      await screen.findByRole('textbox', {name: 'new-variables[0].value'}),
      {target: {value: '"valid_value"'}},
    );

    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].name'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {name: 'new-variables[0].value'}),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Unclaim'}));
    expect(
      await screen.findByRole('button', {name: 'Claim'}),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Claim'}));
    expect(
      await screen.findByRole('button', {name: 'Unclaim'}),
    ).toBeInTheDocument();

    expect(screen.getByText(/Task has no Variables/)).toBeInTheDocument();
  });
});
