/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  fireEvent,
} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetAllOpenTasks, mockGetUnclaimed} from 'modules/queries/get-tasks';
import {generateTask} from 'modules/mock-schema/mocks/tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {Layout} from './index';
import {ApolloProvider} from '@apollo/client';
import {createApolloClient} from 'modules/apollo-client';
import {graphql} from 'msw';
import {mockServer} from 'modules/mockServer';
import userEvent from '@testing-library/user-event';

const mockApolloClient = createApolloClient({maxTasksDisplayed: 5});

const Wrapper: React.FC = ({children}) => {
  return (
    <ApolloProvider client={mockApolloClient}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </ApolloProvider>
  );
};

describe('<Layout />', () => {
  it('should load tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (req, res, ctx) => {
        const {state, assigned} = req.variables;

        if (state === 'CREATED' && assigned === undefined) {
          return res(ctx.data(mockGetAllOpenTasks().result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid query',
            },
          ]),
        );
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTasks', (req, res, ctx) => {
        const {state, assigned} = req.variables;

        if (state === 'CREATED' && assigned) {
          return res(ctx.data(mockGetUnclaimed.result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid query',
            },
          ]),
        );
      }),
    );

    render(<Layout />, {
      wrapper: Wrapper,
    });

    expect(screen.getByLabelText(/filter/i)).toBeDisabled();
    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(screen.getByLabelText(/filter/i)).toBeEnabled();
    expect(
      screen.queryByTestId('tasks-loading-overlay'),
    ).not.toBeInTheDocument();

    userEvent.selectOptions(screen.getByLabelText(/filter/i), ['unclaimed']);

    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();
    expect(screen.getByLabelText(/filter/i)).toBeDisabled();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(screen.getByLabelText(/filter/i)).toBeEnabled();
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
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('3'), generateTask('4')],
          }),
        );
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('5'), generateTask('6')],
          }),
        );
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('7'), generateTask('8')],
          }),
        );
      }),
    );

    render(<Layout />, {
      wrapper: Wrapper,
    });

    expect(screen.getByLabelText(/filter/i)).toBeDisabled();
    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();

    fireEvent.scroll(screen.getByTestId('scrollable-list'), {
      target: {scrollY: 100},
    });

    expect(await screen.findByText('TASK 5')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();

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
