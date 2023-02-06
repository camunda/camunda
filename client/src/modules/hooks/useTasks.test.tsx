/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  fireEvent,
} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {ApolloProvider} from '@apollo/client';
import {createApolloClient} from 'modules/apollo-client';
import {graphql} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {useTasks} from './useTasks';
import {generateTask} from 'modules/mock-schema/mocks/tasks';

jest.mock('modules/constants/tasks', () => ({
  MAX_TASKS_DISPLAYED: 3,
  MAX_TASKS_PER_REQUEST: 2,
}));

const MockComponent: React.FC<{withPolling: boolean}> = ({withPolling}) => {
  const {
    tasks,
    loading,
    shouldFetchMoreTasks,
    fetchPreviousTasks,
    fetchNextTasks,
  } = useTasks({withPolling});

  return (
    <div>
      <button
        aria-label="fetch-previous"
        onClick={() => shouldFetchMoreTasks && fetchPreviousTasks()}
      ></button>
      <button
        aria-label="fetch-next"
        onClick={() => shouldFetchMoreTasks && fetchNextTasks()}
      ></button>
      {loading && <div>Loading</div>}
      {shouldFetchMoreTasks && <div>can fetch more tasks</div>}
      <ul>
        {tasks.map((task) => (
          <li key={task.id}>{task.name}</li>
        ))}
      </ul>
    </div>
  );
};

const mockApolloClient = createApolloClient({maxTasksDisplayed: 3});

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ApolloProvider client={mockApolloClient}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </ApolloProvider>
  );
};

describe('useTasks', () => {
  it('should fetch prev and next tasks', async () => {
    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('1'), generateTask('2')],
          }),
        );
      }),
    );

    render(<MockComponent withPolling={false} />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(screen.getByText('Loading'));

    expect(screen.getByText('TASK 1')).toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();

    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('3'), generateTask('4')],
          }),
        );
      }),
    );

    fireEvent.click(screen.getByRole('button', {name: 'fetch-next'}));

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();

    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();

    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('5'), generateTask('6')],
          }),
        );
      }),
    );

    fireEvent.click(screen.getByRole('button', {name: 'fetch-next'}));

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();

    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 2')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 3')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 5')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();

    nodeMockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('2'), generateTask('3')],
          }),
        );
      }),
    );

    fireEvent.click(screen.getByRole('button', {name: 'fetch-previous'}));

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();

    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.queryByText('TASK 5')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 6')).not.toBeInTheDocument();
  });

  it('should poll', async () => {
    jest.useFakeTimers();

    nodeMockServer.use(
      graphql.query('GetTasks', (req, res, ctx) => {
        const variables = req?.body?.variables ?? {};

        // initial request
        if (
          variables.state === 'CREATED' &&
          variables.pageSize === 2 &&
          variables.searchAfterOrEqual === undefined &&
          variables.searchBefore === undefined &&
          variables.searchAfter === undefined
        ) {
          return res(
            ctx.data({
              tasks: [generateTask('1'), generateTask('2')],
            }),
          );
        }

        // polling
        if (
          variables.state === 'CREATED' &&
          variables.pageSize === 2 &&
          variables.searchAfterOrEqual?.includes('1') &&
          variables.searchBefore === undefined &&
          variables.searchAfter === undefined
        ) {
          return res(
            ctx.data({
              tasks: [
                generateTask('1', 'task1-updated'),
                generateTask('2', 'task2-updated'),
              ],
            }),
          );
        }

        if (
          variables.state === 'CREATED' &&
          variables.pageSize === 3 &&
          variables.searchAfterOrEqual?.includes('1') &&
          variables.searchBefore === undefined &&
          variables.searchAfter === undefined
        ) {
          return res(
            ctx.data({
              tasks: [
                generateTask('1', 'task1-updated2'),
                generateTask('2', 'task2-updated2'),
                generateTask('3', 'task3-updated2'),
              ],
            }),
          );
        }
        if (
          variables.state === 'CREATED' &&
          variables.pageSize === 3 &&
          variables.searchAfterOrEqual?.includes('2') &&
          variables.searchBefore === undefined &&
          variables.searchAfter === undefined
        ) {
          return res(
            ctx.data({
              tasks: [
                generateTask('2', 'task2-updated3'),
                generateTask('3', 'task3-updated3'),
                generateTask('4', 'task4-updated3'),
              ],
            }),
          );
        }
        // fetch next
        if (
          variables.state === 'CREATED' &&
          variables.pageSize === 2 &&
          variables.searchAfter?.includes('2') &&
          variables.searchBefore === undefined &&
          variables.searchAfterOrEqual === undefined
        ) {
          return res(
            ctx.data({
              tasks: [generateTask('3'), generateTask('4')],
            }),
          );
        }
        // fetch prev
        if (
          variables.state === 'CREATED' &&
          variables.pageSize === 2 &&
          variables.searchAfter === undefined &&
          variables.searchBefore?.includes('2') &&
          variables.searchAfterOrEqual === undefined
        ) {
          return res(
            ctx.data({
              tasks: [generateTask('1')],
            }),
          );
        }

        return res(ctx.status(404));
      }),
    );

    render(<MockComponent withPolling={true} />, {
      wrapper: Wrapper,
    });
    await waitForElementToBeRemoved(screen.getByText('Loading'));

    expect(screen.getByText('TASK 1')).toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();

    jest.advanceTimersByTime(5000);

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();

    expect(screen.getByText('task1-updated')).toBeInTheDocument();
    expect(screen.getByText('task2-updated')).toBeInTheDocument();

    // fetch next tasks
    fireEvent.click(screen.getByRole('button', {name: 'fetch-next'}));

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();

    expect(screen.getByText('task2-updated')).toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();

    jest.advanceTimersByTime(5000);

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();

    expect(screen.getByText('task2-updated3')).toBeInTheDocument();
    expect(screen.getByText('task3-updated3')).toBeInTheDocument();
    expect(screen.getByText('task4-updated3')).toBeInTheDocument();

    // fetch previous tasks
    fireEvent.click(screen.getByRole('button', {name: 'fetch-previous'}));

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();
    expect(screen.getByText('TASK 1')).toBeInTheDocument();
    expect(screen.getByText('task2-updated3')).toBeInTheDocument();
    expect(screen.getByText('task3-updated3')).toBeInTheDocument();

    jest.advanceTimersByTime(5000);

    await waitForElementToBeRemoved(screen.getByText('can fetch more tasks'));
    expect(await screen.findByText('can fetch more tasks')).toBeInTheDocument();
    expect(screen.getByText('task1-updated2')).toBeInTheDocument();
    expect(screen.getByText('task2-updated2')).toBeInTheDocument();
    expect(screen.getByText('task3-updated2')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
