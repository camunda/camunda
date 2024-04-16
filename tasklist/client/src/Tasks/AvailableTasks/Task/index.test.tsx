/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
  beforeEach(() => {
    vi.useFakeTimers({now: Date.parse('2024-05-30T00:00:00.000Z')});
  });
  afterEach(() => {
    vi.useRealTimers();
  });

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
    expect(screen.getByTitle('Created Yesterday at 14:00')).toBeInTheDocument();
    expect(screen.getByTitle('Created Yesterday at 14:00')).toHaveTextContent(
      'Yesterday, 14:00',
    );
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

    expect(screen.getByTestId('creation-time')).toBeEmptyDOMElement();
  });

  it('should navigate to task detail on click', () => {
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

    expect(screen.getByTitle('Due on 29 May 2025')).toBeInTheDocument();
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

    expect(screen.getByTitle('Due on 29 May 2025')).toBeInTheDocument();
    expect(screen.queryByText('Follow-up on')).not.toBeInTheDocument();
    expect(screen.queryByText('Completed on')).not.toBeInTheDocument();
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

    expect(screen.getByTitle('Follow-up on 29 May 2025')).toBeInTheDocument();
    expect(screen.queryByText('Due on')).not.toBeInTheDocument();
    expect(screen.queryByText('Completed on')).not.toBeInTheDocument();
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

    expect(screen.getByTitle('Completed on 28 May 2025')).toBeInTheDocument();
    expect(screen.queryByText('Due on')).not.toBeInTheDocument();
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
        context="My Task"
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
      screen.getByTitle(`Overdue on ${formattedDate}`),
    ).toBeInTheDocument();
    expect(screen.queryByText('Due on')).not.toBeInTheDocument();
  });
});
