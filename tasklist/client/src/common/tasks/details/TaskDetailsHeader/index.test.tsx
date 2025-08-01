/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import * as userMocks from 'common/mocks/current-user';
import {TaskDetailsHeader} from './index';

const mockTasks = {
  completedTask: {
    name: 'My Task',
    processName: 'Nice Process',
    assignee: userMocks.currentUser.username,
    taskState: 'COMPLETED',
  },
  unassignedTask: {
    name: 'My Task',
    processName: 'Nice Process',
    assignee: null,
    taskState: 'CREATED',
  },
  assignedTask: {
    name: 'My Task',
    processName: 'Nice Process',
    assignee: userMocks.currentUser.username,
    taskState: 'CREATED',
  },
} as const;

describe('<TaskDetailsHeader />', () => {
  it('should render completed task details', () => {
    render(
      <TaskDetailsHeader
        taskName={mockTasks.completedTask.name}
        processName={mockTasks.completedTask.processName}
        assignee={mockTasks.completedTask.assignee}
        taskState={mockTasks.completedTask.taskState}
        user={userMocks.currentUser}
        assignButton={<button type="button">Assign to me</button>}
      />,
    );

    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Completed by')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^assign to me$/i}),
    ).not.toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    render(
      <TaskDetailsHeader
        taskName={mockTasks.unassignedTask.name}
        processName={mockTasks.unassignedTask.processName}
        assignee={mockTasks.unassignedTask.assignee}
        taskState={mockTasks.unassignedTask.taskState}
        user={userMocks.currentUser}
        assignButton={<button type="button">Assign to me</button>}
      />,
    );

    expect(
      screen.getByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should render assigned task details', async () => {
    render(
      <TaskDetailsHeader
        taskName={mockTasks.assignedTask.name}
        processName={mockTasks.assignedTask.processName}
        assignee={mockTasks.assignedTask.assignee}
        taskState={mockTasks.assignedTask.taskState}
        user={userMocks.currentUser}
        assignButton={<button type="button">Unassign task</button>}
      />,
    );

    expect(
      screen.getByRole('button', {name: /^unassign task$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
  });

  it('should render a task assigned to someone else', () => {
    const MOCK_OTHER_ASSIGNEE = 'jane';

    render(
      <TaskDetailsHeader
        taskName={mockTasks.assignedTask.name}
        processName={mockTasks.assignedTask.processName}
        assignee={MOCK_OTHER_ASSIGNEE}
        taskState={mockTasks.assignedTask.taskState}
        user={userMocks.currentUser}
        assignButton={<button type="button">Assign Button</button>}
      />,
    );

    expect(screen.getByTestId('assignee')).toHaveTextContent(
      `Assigned to ${MOCK_OTHER_ASSIGNEE}`,
    );
  });
});
