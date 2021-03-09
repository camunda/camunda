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
  mockGetTaskClaimedWithVariables,
  mockGetTaskClaimed,
  mockGetTaskCompletedWithVariables,
} from 'modules/queries/get-task';
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

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

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
        mocks: [mockGetTaskClaimedWithVariables, mockGetCurrentUser],
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
        mocks: [mockGetTaskCompletedWithVariables, mockGetCurrentUser],
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
          mockGetTaskClaimed,
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

    expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
      headline: 'Task completed',
    });
  });

  it('should get error on complete task', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [mockGetTaskClaimedWithVariables, mockGetCurrentUser],
      }),
    });

    fireEvent.click(await screen.findByRole('button', {name: 'Complete Task'}));

    await waitFor(() => {
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Task could not be completed',
        description: 'Service is not reachable',
      });
    });
  });

  it('should display gse notification on complete task', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0?gseUrl=https://www.testUrl.com'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskClaimed,
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

    expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
      headline: 'To continue to getting started, go back to',
      isDismissable: false,
      isGseNotification: true,
      navigation: expect.objectContaining({
        label: 'Cloud',
      }),
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
          mockGetTaskClaimedWithVariables,
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

    expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
      headline: 'Task completed',
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
          mockGetTaskClaimed,
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

    expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
      headline: 'Task completed',
    });
  });

  it('should disable submit button on form errors for existing variables', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [mockGetTaskClaimedWithVariables, mockGetCurrentUser],
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
        mocks: [mockGetTaskClaimedWithVariables, mockGetCurrentUser],
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
        mocks: [mockGetTaskClaimedWithVariables, mockGetCurrentUser],
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
          mockGetTaskClaimed,
          mockUnclaimTask,
          mockGetAllOpenTasks,
          mockClaimTask,
          mockGetAllOpenTasks,
          mockGetTaskClaimed,
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
