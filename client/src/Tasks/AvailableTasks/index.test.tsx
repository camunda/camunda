/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {AvailableTasks} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Link, MemoryRouter} from 'react-router-dom';
import {
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
} from 'modules/queries/get-tasks';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {graphql} from 'msw';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';

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
    <ApolloProvider client={client}>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          {children}
          <Link to="/">go home</Link>
        </MemoryRouter>
      </MockThemeProvider>
    </ApolloProvider>
  );

  return Wrapper;
};

describe('<Tasks />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
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
        tasks={mockGetAllOpenTasks}
      />,
    );

    expect(screen.queryByTestId('tasks-skeleton')).not.toBeInTheDocument();
    expect(screen.getByTestId('task-0')).toBeInTheDocument();
  });

  it('should render tasks', async () => {
    render(
      <AvailableTasks
        loading={false}
        onScrollDown={noop}
        onScrollUp={noop}
        tasks={mockGetAllOpenTasks}
      />,
      {wrapper: getWrapper()},
    );

    const [firstTask, secondTask] = mockGetAllOpenTasks;

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
        tasks={mockGetEmptyTasks}
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByText('No tasks found')).toBeInTheDocument();
    expect(
      screen.getByText('There are no tasks matching your filter criteria.'),
    ).toBeInTheDocument();
  });
});
