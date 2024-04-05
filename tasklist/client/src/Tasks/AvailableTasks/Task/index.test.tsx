/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, fireEvent} from 'modules/testing-library';
import {Task} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {LocationLog} from 'modules/utils/LocationLog';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {format, subDays} from 'date-fns';

const createWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MockThemeProvider>
      <MemoryRouter initialEntries={initialEntries}>
        {children}
        <LocationLog />
      </MemoryRouter>
    </MockThemeProvider>
  );

  return Wrapper;
};

describe('<Task />', () => {
  it('should render task', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
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
      screen.getByTitle('Created at 29 May 2024 - 02:00 PM'),
    ).toBeInTheDocument();
    expect(screen.getByText('Me')).toBeInTheDocument();
  });

  it('should handle unassigned tasks', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={null}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
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
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="invalid date"
        assignee={currentUser.userId}
        context="My Task"
        followUpDate={null}
        dueDate={null}
        completionDate={null}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTestId('creation-time')).toBeEmptyDOMElement();
  });

  it('should navigate to task detail on click', () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        assignee={currentUser.userId}
        context="My Task"
        followUpDate={null}
        dueDate={null}
        completionDate={null}
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
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={null}
        completionDate={null}
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
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate={null}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTitle('Due at 29 May 2025')).toBeInTheDocument();
  });

  it('should render a task with due date when filtered by due date', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.userId}
        followUpDate="2025-05-29T14:00:00.000Z"
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate={null}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(['/?sortBy=due']),
      },
    );

    expect(screen.getByTitle('Due at 29 May 2025')).toBeInTheDocument();
    expect(screen.queryByText('Follow-up at')).not.toBeInTheDocument();
    expect(screen.queryByText('Completed at')).not.toBeInTheDocument();
  });

  it('should render a task with follow-up date when filtered by follow-up date', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.userId}
        followUpDate="2025-05-29T14:00:00.000Z"
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate={null}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(['/?sortBy=follow-up']),
      },
    );

    expect(screen.getByTitle('Follow-up at 29 May 2025')).toBeInTheDocument();
    expect(screen.queryByText('Due at')).not.toBeInTheDocument();
    expect(screen.queryByText('Completed at')).not.toBeInTheDocument();
  });

  it('should render a task with completion date', async () => {
    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="My Task"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate="2025-05-29T14:00:00.000Z"
        completionDate="2025-05-28T14:00:00.000Z"
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByTitle('Completed at 28 May 2025')).toBeInTheDocument();
    expect(screen.queryByText('Due at')).not.toBeInTheDocument();
  });

  it('should render a task with overdue date', async () => {
    const todaysDate = new Date().toISOString();
    const yesterdaysDate = subDays(todaysDate, 1).toISOString();
    const formattedDate = format(yesterdaysDate, 'dd MMM yyyy');

    render(
      <Task
        taskId="1"
        name="name"
        processName="processName"
        creationDate="2024-05-29T14:00:00.000Z"
        context="name"
        assignee={currentUser.userId}
        followUpDate={null}
        dueDate={yesterdaysDate}
        completionDate={null}
        currentUser={userMocks.currentUser}
        position={0}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(
      screen.getByTitle(`Overdue at ${formattedDate}`),
    ).toBeInTheDocument();
    expect(screen.queryByText('Due at')).not.toBeInTheDocument();
  });
});
