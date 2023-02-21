/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Details} from '.';
import {render, screen, waitFor} from '@testing-library/react';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
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
import {ApolloProvider, useQuery} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql} from 'msw';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
} from 'modules/queries/get-current-user';
import userEvent from '@testing-library/user-event';

const UserName = () => {
  const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  return <div>{data?.currentUser.displayName}</div>;
};

const getWrapper = (id: string = '0') => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <ApolloProvider client={client}>
      <UserName />
      <MockThemeProvider>
        <MemoryRouter initialEntries={[`/${id}`]}>
          <Routes>
            <Route path="/:id" element={children} />
          </Routes>
        </MemoryRouter>
      </MockThemeProvider>
    </ApolloProvider>
  );

  return Wrapper;
};

describe('<Details />', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render completed task details', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskCompleted().result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'demo',
    );
    expect(
      screen.queryByRole('button', {name: /^unclaim$/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/2020-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).not.toHaveTextContent(
      'Unassigned - claim task to work on this task.',
    );
  });

  it('should render unclaimed task details', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskUnclaimed().result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /^claim$/i}),
    ).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Unassigned - claim task to work on this task.',
    );
    expect(
      screen.getByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(screen.queryByText('Completion Time')).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Unassigned - claim task to work on this task.',
    );
  });

  it('should render unclaimed task and claim it', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskUnclaimed().result.data));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks(true).result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
        'Unassigned - claim task to work on this task.',
      ),
    );

    userEvent.click(await screen.findByRole('button', {name: /^claim$/i}));

    expect(
      screen.queryByRole('button', {name: /^claim$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Claiming...')).toBeInTheDocument();
    expect(await screen.findByText('Claiming successful')).toBeInTheDocument();
    expect(screen.queryByText('Claiming...')).not.toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /^unclaim$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Claiming successful')).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'demo',
    );
    expect(screen.getByTestId('assignee-task-details')).not.toHaveTextContent(
      'Unassigned - claim task to work on this task.',
    );
  });

  it('should render claimed task and unclaim it', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetAllOpenTasksUnclaimed(true).result.data),
        );
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('button', {name: /^unclaim$/i}),
    ).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).not.toHaveTextContent(
      'Unassigned - claim task to work on this task.',
    );

    userEvent.click(screen.getByRole('button', {name: /^unclaim$/i}));

    expect(screen.getByText('Unclaiming...')).toBeInTheDocument();
    expect(
      await screen.findByText('Unclaiming successful'),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /^claim$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Unclaiming...')).not.toBeInTheDocument();
    expect(screen.queryByText('Unclaiming successful')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unclaim$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Unassigned - claim task to work on this task.',
    );
  });

  it('should not render `unclaim task` for restricted users', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetAllOpenTasksUnclaimed(true).result.data),
        );
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Nice Process')).toBeInTheDocument();
    expect(await screen.findByText('demo')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /^unclaim$/i}),
    ).not.toBeInTheDocument();
  });

  it('should not render `claim task` for restricted users', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskUnclaimed().result.data));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks(true).result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Nice Process')).toBeInTheDocument();
    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^claim$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent('--');
  });
});
