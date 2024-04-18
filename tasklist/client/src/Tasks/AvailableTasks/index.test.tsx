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

import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {AvailableTasks} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Link, MemoryRouter} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as tasksMocks from 'modules/mock-schema/mocks/tasks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

function noop() {
  return Promise.resolve([]);
}

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          {children}
          <Link to="/">go home</Link>
        </MemoryRouter>
      </MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<Tasks />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
    );
  });

  it('should not render when loading', async () => {
    const {rerender} = render(
      <AvailableTasks
        loading
        onScrollDown={noop}
        onScrollUp={noop}
        tasks={[]}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.queryByTestId('task-0')).not.toBeInTheDocument();
    expect(screen.getByTestId('tasks-skeleton')).toBeInTheDocument();

    rerender(
      <AvailableTasks
        loading={false}
        onScrollDown={noop}
        onScrollUp={noop}
        tasks={tasksMocks.tasks}
      />,
    );

    await waitForElementToBeRemoved(screen.queryByTestId('tasks-skeleton'));

    expect(screen.queryByTestId('tasks-skeleton')).not.toBeInTheDocument();
    expect(screen.getByTestId('task-0')).toBeInTheDocument();
  });

  it('should render tasks', async () => {
    render(
      <AvailableTasks
        loading={false}
        onScrollDown={noop}
        onScrollUp={noop}
        tasks={tasksMocks.tasks}
      />,
      {wrapper: getWrapper()},
    );

    await waitForElementToBeRemoved(screen.queryByTestId('tasks-skeleton'));

    const [firstTask, secondTask] = tasksMocks.tasks;

    const withinFirstTask = within(screen.getByTestId('task-0'));
    const withinSecondTask = within(screen.getByTestId('task-1'));

    expect(withinFirstTask.getByText(firstTask.name)).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.processName),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByTitle('Created on 28th of May at 10:11'),
    ).toBeInTheDocument();
    expect(await withinFirstTask.findByText('Me')).toBeInTheDocument();

    expect(withinSecondTask.getByText(secondTask.name)).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.processName),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByTitle('Created on 29th of May at 13:14'),
    ).toBeInTheDocument();
    expect(withinSecondTask.getByText('mustermann')).toBeInTheDocument();
  });

  it('should render empty message when there are no tasks', async () => {
    render(
      <AvailableTasks
        loading={false}
        onScrollDown={noop}
        onScrollUp={noop}
        tasks={[]}
      />,
      {wrapper: getWrapper()},
    );

    await waitForElementToBeRemoved(screen.queryByTestId('tasks-skeleton'));

    expect(screen.getByText('No tasks found')).toBeInTheDocument();
    expect(
      screen.getByText('There are no tasks matching your filter criteria.'),
    ).toBeInTheDocument();
  });
});
