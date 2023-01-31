/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';
import {AvailableTasks} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Link, MemoryRouter} from 'react-router-dom';
import {
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
} from 'modules/queries/get-tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {FilterValues} from 'modules/constants/filterValues';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {graphql} from 'msw';
import userEvent from '@testing-library/user-event';

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MockThemeProvider>
      <ApolloProvider client={client}>
        <MemoryRouter initialEntries={initialEntries}>
          {children}
          <Link to="/">go home</Link>
        </MemoryRouter>
      </ApolloProvider>
    </MockThemeProvider>
  );

  return Wrapper;
};

describe('<Tasks />', () => {
  it('should not render when loading', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<AvailableTasks />, {wrapper: getWrapper()});

    expect(screen.queryByTestId('task-0')).not.toBeInTheDocument();
    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));
    expect(screen.getByTestId('task-0')).toBeInTheDocument();
  });

  it('should render tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
    );

    render(<AvailableTasks />, {wrapper: getWrapper()});

    const [firstTask, secondTask] = mockGetAllOpenTasks().result.data.tasks;

    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));

    const withinFirstTask = within(screen.getByTestId('task-0'));
    const withinSecondTask = within(screen.getByTestId('task-1'));

    expect(withinFirstTask.getByText(firstTask.name)).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.processName),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.creationTime),
    ).toBeInTheDocument();
    expect(withinFirstTask.getByText(firstTask.assignee!)).toBeInTheDocument();

    expect(withinSecondTask.getByText(secondTask.name)).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.processName),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.creationTime),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.assignee!),
    ).toBeInTheDocument();
  });

  it('should render empty message when there are no tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetEmptyTasks.result.data));
      }),
    );

    render(<AvailableTasks />, {wrapper: getWrapper()});

    expect(await screen.findByText('No tasks found')).toBeInTheDocument();
    expect(
      screen.getByText('There are no tasks matching your filter criteria.'),
    ).toBeInTheDocument();
  });

  it('should show all tasks claimed by me', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetClaimedByMe.result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    render(<AvailableTasks />, {
      wrapper: getWrapper([`/?filter=${FilterValues.ClaimedByMe}`]),
    });

    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));

    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
    expect(screen.getByTestId('task-2')).toBeInTheDocument();
  });

  it('should show all unclaimed tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetUnclaimed.result.data));
      }),
    );

    render(<AvailableTasks />, {
      wrapper: getWrapper([`/?filter=${FilterValues.Unclaimed}`]),
    });

    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));

    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
    expect(screen.getByTestId('task-2')).toBeInTheDocument();
  });

  it('should show all completed tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCompleted.result.data));
      }),
    );

    render(<AvailableTasks />, {
      wrapper: getWrapper([`/?filter=${FilterValues.Completed}`]),
    });

    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));

    expect(screen.getByTestId('task-0')).toBeInTheDocument();
    expect(screen.getByTestId('task-1')).toBeInTheDocument();
    expect(screen.getByTestId('task-2')).toBeInTheDocument();
  });

  it('should show a skeleton while changing filters', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks().result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCompleted.result.data));
      }),
    );

    render(<AvailableTasks />, {
      wrapper: getWrapper([`/?filter=${FilterValues.Completed}`]),
    });

    expect(screen.getByTestId('tasks-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));

    userEvent.click(screen.getByRole('link', {name: /go home/i}));

    expect(screen.getByTestId('tasks-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() => screen.getByTestId('tasks-skeleton'));
  });
});
