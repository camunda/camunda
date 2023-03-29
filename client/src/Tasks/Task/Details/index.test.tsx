/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Details} from '.';
import {render, screen} from 'modules/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
} from 'modules/queries/get-task';
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
import noop from 'lodash/noop';

const UserName = () => {
  const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  return <div>{data?.currentUser.displayName}</div>;
};

const MOCK_TASK_CLAIMED = mockGetTaskClaimed().result.data.task;
const MOCK_TASK_COMPLETED = mockGetTaskCompleted().result.data.task;
const MOCK_UNCLAIMED_TASK = mockGetTaskUnclaimed().result.data.task;

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
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

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
    );

    render(<Details task={MOCK_TASK_COMPLETED} onAssigmentError={noop} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unclaim$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('01 Jan 2019 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2020 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('No candidates')).toBeInTheDocument();
  });

  it('should render unclaimed task details', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    render(<Details task={MOCK_UNCLAIMED_TASK} onAssigmentError={noop} />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /^claim$/i}),
    ).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2019 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('Pending task')).toBeInTheDocument();
    expect(screen.getByText('accounting candidate')).toBeInTheDocument();
    expect(screen.getByText('jane candidate')).toBeInTheDocument();
  });

  it('should render unclaimed task and claim it', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
    );

    const {user, rerender} = render(
      <Details task={MOCK_UNCLAIMED_TASK} onAssigmentError={noop} />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      await screen.findByRole('button', {
        name: 'Claim',
      }),
    );

    expect(
      screen.queryByRole('button', {name: /^claim$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Claiming...')).toBeInTheDocument();
    expect(await screen.findByText('Claiming successful')).toBeInTheDocument();
    expect(screen.queryByText('Claiming...')).not.toBeInTheDocument();

    rerender(<Details task={MOCK_TASK_CLAIMED} onAssigmentError={noop} />);

    expect(
      await screen.findByRole('button', {name: /^unclaim$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Claiming successful')).not.toBeInTheDocument();
    expect(screen.getByText('Assigned')).toBeInTheDocument();
  });

  it('should render claimed task and unclaim it', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
    );

    const {user, rerender} = render(
      <Details task={MOCK_TASK_CLAIMED} onAssigmentError={noop} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^unclaim$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('Assigned')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^unclaim$/i}));

    expect(screen.getByText('Unclaiming...')).toBeInTheDocument();
    expect(
      await screen.findByText('Unclaiming successful'),
    ).toBeInTheDocument();

    rerender(<Details task={MOCK_UNCLAIMED_TASK} onAssigmentError={noop} />);

    expect(
      await screen.findByRole('button', {name: /^claim$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Unclaiming...')).not.toBeInTheDocument();
    expect(screen.queryByText('Unclaiming successful')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unclaim$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should not render assignment button on claimed tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
    );

    render(<Details task={MOCK_TASK_CLAIMED} onAssigmentError={noop} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unclaim$/i}),
    ).not.toBeInTheDocument();
  });

  it('should not render assignment on unclaimed tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
    );

    render(<Details task={MOCK_UNCLAIMED_TASK} onAssigmentError={noop} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^claim$/i}),
    ).not.toBeInTheDocument();
  });
});
