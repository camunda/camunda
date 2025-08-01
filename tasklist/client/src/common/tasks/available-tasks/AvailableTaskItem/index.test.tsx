/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, fireEvent} from 'common/testing/testing-library';
import {AvailableTaskItem} from './index';
import {MemoryRouter} from 'react-router-dom';
import {currentUser} from 'common/mocks/current-user';
import {LocationLog} from 'common/testing/LocationLog';
import * as userMocks from 'common/mocks/current-user';

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MemoryRouter initialEntries={initialEntries}>
      {children}
      <LocationLog />
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Task />', () => {
  beforeEach(() => {
    vi.useFakeTimers({now: Date.parse('2024-05-30T00:00:00.000Z')});
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('should render task', () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getByText('processName')).toBeInTheDocument();
    expect(
      screen.getByTitle('Created Yesterday at 2:00 PM'),
    ).toBeInTheDocument();
    expect(screen.getByTitle('Created Yesterday at 2:00 PM')).toHaveTextContent(
      'Yesterday, 2:00 PM',
    );
    expect(screen.getByText('Me')).toBeInTheDocument();
  });

  it('should handle unassigned tasks', () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={null}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should render creation time as empty value if given date is invalid', () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="invalid date"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTestId('dates')).toBeEmptyDOMElement();
  });

  it('should navigate to task detail on click', () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    fireEvent.click(screen.getByText('processName'));
    expect(screen.getByTestId('pathname')).toHaveTextContent('/1');
  });

  it('should preserve search params', () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(['/?filter=all-open']),
      },
    );

    fireEvent.click(screen.getByText('processName'));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/1');
    expect(screen.getByTestId('search')).toHaveTextContent('filter=all-open');
    expect(screen.getByTestId('search')).toHaveTextContent('ref=');
  });

  it('should render a task with due date', async () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTitle('Due on 29th of May, 2025')).toBeInTheDocument();
  });

  it('should render a task with due date when filtered by due date', async () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate="2025-05-29T14:00:00.000Z"
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(['/?sortBy=due']),
      },
    );

    expect(screen.getByTitle('Due on 29th of May, 2025')).toBeInTheDocument();
    expect(screen.queryByText('Follow-up')).not.toBeInTheDocument();
    expect(screen.queryByText('Completed')).not.toBeInTheDocument();
  });

  it('should render a task with follow-up date when filtered by follow-up date', async () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate="2025-05-29T14:00:00.000Z"
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(['/?sortBy=follow-up']),
      },
    );

    expect(
      screen.getByTitle('Follow-up on 29th of May, 2025'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Due')).not.toBeInTheDocument();
    expect(screen.queryByText('Completed')).not.toBeInTheDocument();
  });

  it('should render a task with completion date', async () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate="2025-05-28T14:00:00.000Z"
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(
      screen.getByTitle('Completed on 28th of May, 2025'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Due')).not.toBeInTheDocument();
  });

  it('should render a task with overdue date', async () => {
    render(
      <AvailableTaskItem
        taskId="1"
        displayName="name"
        processDisplayName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.username}
        followUpDate={null}
        dueDate="2024-05-29T00:00:00.000Z"
        completionDate={null}
        priority={50}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTitle(`Overdue Yesterday`)).toBeInTheDocument();
    expect(screen.queryByText('Due')).not.toBeInTheDocument();
  });
});
