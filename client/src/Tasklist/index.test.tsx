/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  fireEvent,
} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {MockedResponse} from '@apollo/client/testing';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetAllOpenTasks, mockGetUnclaimed} from 'modules/queries/get-tasks';
import {generateTask} from 'modules/mock-schema/mocks/tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';

import {Tasklist} from './index';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {graphql} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/constants/tasks', () => ({
  MAX_TASKS_DISPLAYED: 5,
}));

const getWrapper = (mock: MockedResponse[] = []): React.FC => ({children}) => {
  if (mock.length > 0) {
    return (
      <MockedApolloProvider mocks={mock}>
        <MemoryRouter initialEntries={['/']}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </MemoryRouter>
      </MockedApolloProvider>
    );
  } else {
    return (
      <ApolloProvider client={client}>
        <MemoryRouter initialEntries={['/']}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </MemoryRouter>
      </ApolloProvider>
    );
  }
};

describe('<Tasklist />', () => {
  it('should load tasks', async () => {
    render(<Tasklist />, {
      wrapper: getWrapper([
        mockGetAllOpenTasks,
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]),
    });

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeDisabled();
    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeEnabled();
    expect(
      screen.queryByTestId('tasks-loading-overlay'),
    ).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: 'unclaimed',
      },
    });

    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeEnabled();
    expect(
      screen.queryByTestId('tasks-loading-overlay'),
    ).not.toBeInTheDocument();
  });

  it('should load more tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('1'), generateTask('2')],
          }),
        );
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res(
          ctx.data({
            data: {
              currentUser: {
                firstname: 'Demo',
                lastname: 'User',
                username: 'demo',
                __typename: 'User',
              },
            },
            loading: false,
            error: null,
          }),
        );
      }),
    );

    render(<Tasklist />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeDisabled();
    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('3'), generateTask('4')],
          }),
        );
      }),
    );
    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();

    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('5'), generateTask('6')],
          }),
        );
      }),
    );
    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 5')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();

    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('7'), generateTask('8')],
          }),
        );
      }),
    );
    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 7')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 2')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 3')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 5')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();
    expect(screen.getByText('TASK 8')).toBeInTheDocument();
  });
});
