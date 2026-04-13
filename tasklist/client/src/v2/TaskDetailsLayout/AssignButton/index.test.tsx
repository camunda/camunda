/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'v2/testing/testing-library';
import {AssignButton} from './index';
import {assignedTask, unassignedTask} from 'v2/mocks/task';
import {currentUser} from 'v2/mocks/current-user';
import {getMockQueryClient} from 'v2/testing/getMockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {nodeMockServer} from 'v2/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {notificationsStore} from 'v2/notifications/notifications.store';

vi.mock('v2/notifications/notifications.store', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>{children}</QueryClientProvider>
  );

  return Wrapper;
};

describe('AssignButton', () => {
  it('should assign a task', async () => {
    const mockUnassignedTask = unassignedTask();
    const mockAssignedTask = assignedTask();
    nodeMockServer.use(
      http.post(
        '/v2/user-tasks/:userTaskKey/assignment',
        () => HttpResponse.json(),
        {once: true},
      ),
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => HttpResponse.json(mockAssignedTask),
        {once: true},
      ),
    );

    const {user, rerender} = render(
      <AssignButton
        id={mockUnassignedTask.userTaskKey}
        assignee={null}
        taskState={mockUnassignedTask.state}
        currentUser={currentUser.username}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: 'Assign to me'}));

    expect(await screen.findByText('Assignment successful')).toBeVisible();
    rerender(
      <AssignButton
        id={mockAssignedTask.userTaskKey}
        assignee={currentUser.username}
        taskState={mockAssignedTask.state}
        currentUser={currentUser.username}
      />,
    );

    expect(await screen.findByRole('button', {name: 'Unassign'})).toBeVisible();
  });

  it('should unassign a task', async () => {
    const mockUnassignedTask = unassignedTask();
    const mockAssignedTask = assignedTask();
    nodeMockServer.use(
      http.delete(
        '/v2/user-tasks/:userTaskKey/assignee',
        () => HttpResponse.json(),
        {once: true},
      ),
      http.get(
        '/v2/user-tasks/:userTaskKey',
        () => HttpResponse.json(mockUnassignedTask),
        {once: true},
      ),
    );

    const {user, rerender} = render(
      <AssignButton
        id={mockAssignedTask.userTaskKey}
        assignee={currentUser.username}
        taskState={mockAssignedTask.state}
        currentUser={currentUser.username}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: 'Unassign'}));

    expect(await screen.findByText('Unassignment successful')).toBeVisible();

    rerender(
      <AssignButton
        id={mockUnassignedTask.userTaskKey}
        assignee={null}
        taskState={mockUnassignedTask.state}
        currentUser={currentUser.username}
      />,
    );

    expect(
      await screen.findByRole('button', {name: 'Assign to me'}),
    ).toBeVisible();
  });

  it('should handle failed assignment', async () => {
    const mockUnassignedTask = unassignedTask();

    nodeMockServer.use(
      http.post(
        '/v2/user-tasks/:userTaskKey/assignment',
        () => HttpResponse.error(),
        {once: true},
      ),
    );

    const {user} = render(
      <AssignButton
        id={mockUnassignedTask.userTaskKey}
        assignee={null}
        taskState={mockUnassignedTask.state}
        currentUser={currentUser.username}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: 'Assign to me'}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be assigned',
        isDismissable: true,
      });
    });
  });

  it('should handle failed unassignment', async () => {
    const mockUnassignedTask = unassignedTask();

    nodeMockServer.use(
      http.delete(
        '/v2/user-tasks/:userTaskKey/assignee',
        () => HttpResponse.error(),
        {once: true},
      ),
    );

    const {user} = render(
      <AssignButton
        id={mockUnassignedTask.userTaskKey}
        assignee={currentUser.username}
        taskState={mockUnassignedTask.state}
        currentUser={currentUser.username}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: 'Unassign'}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be unassigned',
        isDismissable: true,
      });
    });
  });

  it('should show error notification with subtitle on forbidden assignment', async () => {
    const mockUnassignedTask = unassignedTask();

    nodeMockServer.use(
      http.post(
        '/v2/user-tasks/:userTaskKey/assignment',
        () =>
          HttpResponse.json(
            {
              type: 'about:blank',
              title: 'FORBIDDEN',
              status: 403,
              detail:
                "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
              instance: '/v2/user-tasks/123/assignment',
            },
            {status: 403},
          ),
        {once: true},
      ),
    );

    const {user} = render(
      <AssignButton
        id={mockUnassignedTask.userTaskKey}
        assignee={null}
        taskState={mockUnassignedTask.state}
        currentUser={currentUser.username}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: 'Assign to me'}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be assigned',
        subtitle:
          "You don't have the necessary permissions. Contact your admin to request access.",
        isDismissable: true,
      });
    });

    expect(
      await screen.findByRole('button', {name: 'Assign to me'}),
    ).toBeVisible();
  });

  it('should show error notification with subtitle on unauthorized unassignment', async () => {
    const mockAssignedTask = assignedTask();

    nodeMockServer.use(
      http.delete(
        '/v2/user-tasks/:userTaskKey/assignee',
        () =>
          HttpResponse.json(
            {
              type: 'about:blank',
              title: 'FORBIDDEN',
              status: 403,
              detail:
                "Unauthorized to perform operation 'UPDATE' on resource 'USER_TASK'",
              instance: '/v2/user-tasks/123/assignee',
            },
            {status: 403},
          ),
        {once: true},
      ),
    );

    const {user} = render(
      <AssignButton
        id={mockAssignedTask.userTaskKey}
        assignee={currentUser.username}
        taskState={mockAssignedTask.state}
        currentUser={currentUser.username}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: 'Unassign'}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be unassigned',
        subtitle:
          "You don't have the necessary permissions. Contact your admin to request access.",
        isDismissable: true,
      });
    });

    expect(await screen.findByRole('button', {name: 'Unassign'})).toBeVisible();
  });
});
