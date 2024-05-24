/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import noop from 'lodash/noop';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {Header} from './Header';

const UserName = () => {
  const {data: currentUser} = useCurrentUser();

  return <div>{currentUser?.displayName}</div>;
};

const getWrapper = (id: string = '0') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <UserName />
      <MemoryRouter initialEntries={[`/${id}`]}>
        <Routes>
          <Route path="/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<Header />', () => {
  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  it('should render completed task details', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );

    render(
      <Header
        task={taskMocks.completedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Completed by')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );

    render(
      <Header
        task={taskMocks.unassignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('My Task')).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should render unassigned task and assign it', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.patch('/v1/tasks/:taskId/assign', () => {
        return HttpResponse.json(taskMocks.assignedTask('0'));
      }),
    );

    const {user, rerender} = render(
      <Header
        task={taskMocks.unassignedTask('0')}
        user={userMocks.currentUser}
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
    expect(
      await screen.findByText('Assignment successful'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assigning...')).not.toBeInTheDocument();

    rerender(
      <Header
        task={taskMocks.assignedTask()}
        user={userMocks.currentUser}
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
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.patch('/v1/tasks/:taskId/unassign', () => {
        return HttpResponse.json(taskMocks.unassignedTask('0'));
      }),
    );

    const {user, rerender} = render(
      <Header
        task={taskMocks.assignedTask('0')}
        user={userMocks.currentUser}
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

    expect(
      await screen.findByText('Unassignment successful'),
    ).toBeInTheDocument();

    rerender(
      <Header
        task={taskMocks.unassignedTask()}
        user={userMocks.currentUser}
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
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentRestrictedUser);
      }),
      http.patch('/v1/tasks/:taskId/unassign', () => {
        return HttpResponse.json(taskMocks.unassignedTask);
      }),
    );

    render(
      <Header
        task={taskMocks.assignedTask()}
        user={userMocks.currentRestrictedUser}
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
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentRestrictedUser);
      }),
      http.patch('/v1/tasks/:taskId/assign', () => {
        return HttpResponse.json(taskMocks.assignedTask);
      }),
    );

    render(
      <Header
        task={taskMocks.unassignedTask()}
        user={userMocks.currentRestrictedUser}
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
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );

    const MOCK_OTHER_ASSIGNEE = 'jane';

    render(
      <Header
        task={{...taskMocks.assignedTask(), assignee: MOCK_OTHER_ASSIGNEE}}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTestId('assignee')).toHaveTextContent(
      `Assigned to ${MOCK_OTHER_ASSIGNEE}`,
    );
  });
});
