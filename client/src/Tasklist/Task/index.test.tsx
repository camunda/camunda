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
  waitFor,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';
import {MockedResponse} from '@apollo/client/testing';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockGetTaskClaimed,
  mockGetTaskCompletedWithForm,
  mockGetTaskClaimedWithForm,
  mockGetTaskCompleted,
} from 'modules/queries/get-task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {mockCompleteTask} from 'modules/mutations/complete-task';
import {mockGetAllOpenTasks} from 'modules/queries/get-tasks';
import {mockClaimTask} from 'modules/mutations/claim-task';
import {mockUnclaimTask} from 'modules/mutations/unclaim-task';
import userEvent from '@testing-library/user-event';
import {mockGetForm, mockGetInvalidForm} from 'modules/queries/get-form';
import {
  mockGetTaskVariables,
  mockGetTaskEmptyVariables,
} from 'modules/queries/get-task-variables';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {graphql} from 'msw';
import {mockServer} from 'modules/mockServer';

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
  const Wrapper: React.FC = ({children}) => {
    if (mocks.length > 0) {
      return (
        <Router history={history}>
          <Route path="/:id">
            <MockedApolloProvider mocks={mocks}>
              <MockThemeProvider>{children}</MockThemeProvider>
            </MockedApolloProvider>
          </Route>
        </Router>
      );
    } else {
      return (
        <ApolloProvider client={client}>
          <Router history={history}>
            <Route path="/:id">
              <MockThemeProvider>{children}</MockThemeProvider>
            </Route>
          </Router>
        </ApolloProvider>
      );
    }
  };

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
          mockGetTaskClaimed(),
          mockGetCurrentUser,
          mockGetTaskClaimedWithForm(),
          mockGetForm,
          mockGetTaskVariables(),
        ],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {
        name: 'Complete Task',
      }),
    ).toBeInTheDocument();
  });

  it('should render created task with embedded form', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetCurrentUser,
          mockGetTaskClaimedWithForm(),
          mockGetForm,
          mockGetTaskVariables(),
        ],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {
        name: 'Complete Task',
      }),
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
          mockGetTaskCompleted(),
          mockGetCurrentUser,
          mockGetTaskVariables(),
        ],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: 'Complete Task',
      }),
    ).not.toBeInTheDocument();
  });

  it('should render completed task with embedded form', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetCurrentUser,
          mockGetTaskCompletedWithForm(),
          mockGetForm,
          mockGetTaskVariables(),
        ],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: 'Complete Task',
      }),
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
          mockGetTaskClaimed(),
          mockGetCurrentUser,
          mockCompleteTask,
          mockGetAllOpenTasks(true),
          mockGetTaskEmptyVariables(),
        ],
      }),
    });

    userEvent.click(
      await screen.findByRole('button', {
        name: 'Complete Task',
      }),
    );

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
        mocks: [
          mockGetTaskClaimed(),
          mockGetCurrentUser,
          mockGetTaskVariables(),
        ],
      }),
    });

    userEvent.click(
      await screen.findByRole('button', {
        name: 'Complete Task',
      }),
    );

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
          mockGetTaskClaimed(),
          mockGetCurrentUser,
          mockCompleteTask,
          mockGetAllOpenTasks(true),
          mockGetTaskEmptyVariables(),
        ],
      }),
    });

    userEvent.click(
      await screen.findByRole('button', {
        name: 'Complete Task',
      }),
    );

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

  it('should show a loading spinner while loading', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [
          mockGetTaskClaimed(),
          mockGetCurrentUser,
          mockGetTaskVariables(),
        ],
      }),
    });

    expect(screen.getByTestId('details-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('details-overlay'));
  });

  it('should reset variables', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/0'],
    });
    render(<Task />, {
      wrapper: getWrapper({
        history: mockHistory,
        mocks: [
          mockGetCurrentUser,
          mockGetTaskClaimed(),
          mockGetTaskEmptyVariables(),
          mockUnclaimTask,
          mockGetAllOpenTasks(true),
          mockClaimTask,
          mockGetAllOpenTasks(true),
          mockGetTaskClaimed('1'),
          mockGetTaskEmptyVariables('1'),
        ],
      }),
    });

    userEvent.click(
      await screen.findByRole('button', {
        name: /Add Variable/,
      }),
    );

    userEvent.type(
      await screen.findByRole('textbox', {
        name: 'New variable 0 name',
      }),
      'valid_name',
    );

    userEvent.type(
      await screen.findByRole('textbox', {
        name: 'New variable 0 value',
      }),
      '"valid_value"',
    );

    expect(
      screen.getByRole('textbox', {
        name: 'New variable 0 name',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('textbox', {
        name: 'New variable 0 value',
      }),
    ).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: 'Unclaim',
      }),
    );
    expect(
      await screen.findByRole('button', {
        name: 'Claim',
      }),
    ).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: 'Claim',
      }),
    );
    expect(
      await screen.findByRole('button', {
        name: 'Unclaim',
      }),
    ).toBeInTheDocument();

    expect(screen.getByText(/Task has no Variables/)).toBeInTheDocument();
  });

  it('should not allow duplicate variables', async () => {
    render(<Task />, {
      wrapper: getWrapper({
        history: createMemoryHistory({
          initialEntries: ['/0'],
        }),
        mocks: [
          mockGetCurrentUser,
          mockGetTaskClaimed(),
          mockUnclaimTask,
          mockGetAllOpenTasks(true),
          mockClaimTask,
          mockGetAllOpenTasks(true),
          mockGetTaskVariables(),
        ],
      }),
    });

    userEvent.click(
      await screen.findByRole('button', {
        name: /Add Variable/,
      }),
    );

    // try to add a variable with a same name from one of the existing variables
    userEvent.type(
      screen.getByRole('textbox', {
        name: 'New variable 0 name',
      }),
      'myVar',
    );

    expect(
      screen.getByTitle('Name must be unique and Value has to be JSON'),
    ).toBeInTheDocument();

    userEvent.clear(
      screen.getByRole('textbox', {
        name: 'New variable 0 name',
      }),
    );
    userEvent.type(
      screen.getByRole('textbox', {
        name: 'New variable 0 name',
      }),
      'myVar2',
    );

    expect(
      await screen.findByTitle('Value has to be JSON'),
    ).toBeInTheDocument();

    // try to add a variable with a same name from one of the new added variables
    userEvent.click(
      screen.getByRole('button', {
        name: /Add Variable/,
      }),
    );
    userEvent.type(
      screen.getByRole('textbox', {
        name: 'New variable 1 name',
      }),
      'myVar2',
    );

    expect(
      within(screen.getByTestId('newVariables[1]')).getByTitle(
        'Name must be unique and Value has to be JSON',
      ),
    ).toBeInTheDocument();

    const withinFirstVariable = within(screen.getByTestId('newVariables[0]'));
    expect(
      withinFirstVariable.queryByTitle(
        'Name must be unique and Value has to be JSON',
      ),
    ).not.toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: 'Remove new variable 0',
      }),
    );

    expect(
      screen.queryByTitle('Name must be unique and Value has to be JSON'),
    ).not.toBeInTheDocument();
  });

  it('should render created task with variables form', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/0'],
    });

    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) =>
        res.once(ctx.data(mockGetCurrentUser.result.data)),
      ),
      graphql.query('GetTask', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskClaimedWithForm().result.data)),
      ),
      graphql.query('GetForm', (_, res, ctx) =>
        res.once(ctx.data(mockGetInvalidForm.result.data)),
      ),
      graphql.query('GetTaskVariables', (_, res, ctx) =>
        res.once(ctx.data(mockGetTaskVariables().result.data)),
      ),
    );

    render(<Task />, {
      wrapper: getWrapper({
        history,
        mocks: [],
      }),
    });

    expect(await screen.findByTestId('details-table')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Invalid Form schema',
    });
  });
});
