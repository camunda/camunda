/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {rest} from 'msw';
import * as tasksMocks from 'modules/mock-schema/mocks/tasks';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import * as userMocks from 'modules/mock-schema/mocks/current-user';

function noop() {
  return Promise.resolve([]);
}

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <ReactQueryProvider>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          {children}
          <Link to="/">go home</Link>
        </MemoryRouter>
      </MockThemeProvider>
    </ReactQueryProvider>
  );

  return Wrapper;
};

describe('<Tasks />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
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

    await waitForElementToBeRemoved(screen.getByTestId('tasks-skeleton'));

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

    await waitForElementToBeRemoved(screen.getByTestId('tasks-skeleton'));

    const [firstTask, secondTask] = tasksMocks.tasks;

    const withinFirstTask = within(screen.getByTestId('task-0'));
    const withinSecondTask = within(screen.getByTestId('task-1'));

    expect(withinFirstTask.getByText(firstTask.name)).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.processName),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByTitle('Created at 28 May 2020 - 10:11 AM'),
    ).toBeInTheDocument();
    expect(
      await withinFirstTask.findByText('Assigned to me'),
    ).toBeInTheDocument();

    expect(withinSecondTask.getByText(secondTask.name)).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.processName),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByTitle('Created at 29 May 2020 - 01:14 PM'),
    ).toBeInTheDocument();
    expect(withinSecondTask.getByText('Assigned')).toBeInTheDocument();
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

    await waitForElementToBeRemoved(screen.getByTestId('tasks-skeleton'));

    expect(screen.getByText('No tasks found')).toBeInTheDocument();
    expect(
      screen.getByText('There are no tasks matching your filter criteria.'),
    ).toBeInTheDocument();
  });
});
