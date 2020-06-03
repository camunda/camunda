/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, within} from '@testing-library/react';

import {Tasks} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {mockGetTasks, mockGetEmptyTasks} from 'modules/queries/get-tasks';
import {MockedResponse} from '@apollo/react-testing';

const getWrapper = (mock: MockedResponse) => {
  const Wrapper: React.FC = ({children}) => (
    <MockedApolloProvider mocks={[mock]}>
      <MemoryRouter>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </MockedApolloProvider>
  );

  return Wrapper;
};

describe('<Tasks />', () => {
  it('should not render when loading', async () => {
    render(<Tasks />, {wrapper: getWrapper(mockGetTasks)});

    expect(screen.queryByTestId('task-0')).not.toBeInTheDocument();
    await screen.findByTestId('task-0');
    expect(screen.getByTestId('task-0')).toBeInTheDocument();
  });

  it('should render tasks', async () => {
    render(<Tasks />, {wrapper: getWrapper(mockGetTasks)});

    const [firstTask, secondTask] = mockGetTasks.result.data.tasks;
    const withinFirstTask = within(await screen.findByTestId('task-0'));
    const withinSecondTask = within(await screen.findByTestId('task-1'));
    expect(withinFirstTask.getByText(firstTask.name)).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.workflowName),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(firstTask.creationTime),
    ).toBeInTheDocument();
    expect(
      withinFirstTask.getByText(
        `${firstTask.assignee?.firstname} ${firstTask.assignee?.lastname}`,
      ),
    ).toBeInTheDocument();

    expect(withinSecondTask.getByText(secondTask.name)).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.workflowName),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(secondTask.creationTime),
    ).toBeInTheDocument();
    expect(
      withinSecondTask.getByText(
        `${secondTask.assignee?.firstname} ${secondTask.assignee?.lastname}`,
      ),
    ).toBeInTheDocument();
  });

  it('should render empty message when there are no tasks', async () => {
    render(<Tasks />, {wrapper: getWrapper(mockGetEmptyTasks)});

    expect(
      await screen.findByText('There are no tasks available.'),
    ).toBeInTheDocument();
  });
});
