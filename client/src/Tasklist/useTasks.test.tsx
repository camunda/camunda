/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import {client} from 'modules/apollo-client';
import {graphql} from 'msw';
import {mockServer} from 'modules/mockServer';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {useTasks} from './useTasks';
import {generateTask} from 'modules/mock-schema/mocks/tasks';

jest.mock('modules/constants/tasks', () => ({
  MAX_TASKS_DISPLAYED: 3,
  MAX_TASKS_PER_REQUEST: 2,
}));

const MockComponent: React.FC = () => {
  const {
    tasks,
    loading,
    shouldFetchMoreTasks,
    fetchPreviousTasks,
    fetchNextTasks,
  } = useTasks();

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
      <ul>
        {tasks.map((task) => (
          <li key={task.id}>{task.name}</li>
        ))}
      </ul>
    </div>
  );
};

const Wrapper: React.FC = ({children}) => {
  return (
    <ApolloProvider client={client}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </ApolloProvider>
  );
};

describe('useTasks', () => {
  it('should fetch prev and next tasks', async () => {
    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('1'), generateTask('2')],
          }),
        );
      }),
    );

    render(<MockComponent />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(screen.getByText('Loading'));

    expect(screen.getByText('TASK 1')).toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();

    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('3'), generateTask('4')],
          }),
        );
      }),
    );

    fireEvent.click(screen.getByRole('button', {name: 'fetch-next'}));

    expect(await screen.findByText('TASK 3')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 2')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();

    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('5'), generateTask('6')],
          }),
        );
      }),
    );

    fireEvent.click(screen.getByRole('button', {name: 'fetch-next'}));

    expect(await screen.findByText('TASK 5')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 2')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 3')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.getByText('TASK 6')).toBeInTheDocument();

    mockServer.use(
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data({
            tasks: [generateTask('2'), generateTask('3')],
          }),
        );
      }),
    );

    fireEvent.click(screen.getByRole('button', {name: 'fetch-previous'}));

    expect(await screen.findByText('TASK 2')).toBeInTheDocument();
    expect(screen.queryByText('TASK 1')).not.toBeInTheDocument();
    expect(screen.getByText('TASK 3')).toBeInTheDocument();
    expect(screen.getByText('TASK 4')).toBeInTheDocument();
    expect(screen.queryByText('TASK 5')).not.toBeInTheDocument();
    expect(screen.queryByText('TASK 6')).not.toBeInTheDocument();
  });
});
