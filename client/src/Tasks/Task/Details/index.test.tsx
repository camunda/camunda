/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Details} from '.';
import {render, screen} from 'modules/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ApolloProvider, useQuery} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql, rest} from 'msw';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
} from 'modules/queries/get-current-user';
import noop from 'lodash/noop';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';

const UserName = () => {
  const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  return <div>{data?.currentUser.displayName}</div>;
};

const getWrapper = (id: string = '0') => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <ReactQueryProvider>
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
    </ReactQueryProvider>
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

    render(
      <Details
        task={taskMocks.completedTask()}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('01 Jan 2019 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2020 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('No candidates')).toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    render(
      <Details
        task={taskMocks.unassignedTask()}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2019 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('Pending task')).toBeInTheDocument();
    expect(screen.getByText('accounting candidate')).toBeInTheDocument();
    expect(screen.getByText('jane candidate')).toBeInTheDocument();
  });

  it('should render unassigned task and assign it', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      rest.patch('/v1/tasks/:taskId/assign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.assignedTask('0')));
      }),
    );

    const {user, rerender} = render(
      <Details
        task={taskMocks.unassignedTask('0')}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      await screen.findByRole('button', {
        name: 'Assign to me',
      }),
    );

    expect(
      screen.queryByRole('button', {name: /^assign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Assigning...')).toBeInTheDocument();
    expect(
      await screen.findByText('Assignment successful'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assigning...')).not.toBeInTheDocument();

    rerender(
      <Details
        task={taskMocks.assignedTask()}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assignment successful')).not.toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
  });

  it('should render assigned task and unassign it', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
      rest.patch('/v1/tasks/:taskId/unassign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.unassignedTask('0')));
      }),
    );

    const {user, rerender} = render(
      <Details
        task={taskMocks.assignedTask('0')}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^unassign$/i}));

    expect(screen.getByText('Unassigning...')).toBeInTheDocument();
    expect(
      await screen.findByText('Unassignment successful'),
    ).toBeInTheDocument();

    rerender(
      <Details
        task={taskMocks.unassignedTask()}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
    );

    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Unassigning...')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Unassignment successful'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should not render assignment button on assigned tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
      rest.patch('/v1/tasks/:taskId/unassign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.unassignedTask));
      }),
    );

    render(
      <Details
        task={taskMocks.assignedTask()}
        user={mockGetCurrentRestrictedUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should not render assignment on unassigned tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
      rest.patch('/v1/tasks/:taskId/assign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.assignedTask));
      }),
    );

    render(
      <Details
        task={taskMocks.unassignedTask()}
        user={mockGetCurrentRestrictedUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^assign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should render a task assigned to someone else', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    const MOCK_OTHER_ASSIGNEE = 'jane';

    render(
      <Details
        task={{...taskMocks.assignedTask(), assignee: MOCK_OTHER_ASSIGNEE}}
        user={mockGetCurrentUser.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTestId('assignee')).toHaveTextContent(
      `Assigned to${MOCK_OTHER_ASSIGNEE}`,
    );
  });
});
