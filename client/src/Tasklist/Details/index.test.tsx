/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Details} from './';

import * as React from 'react';
import {render, screen} from '@testing-library/react';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';

import {Route, MemoryRouter} from 'react-router-dom';
import {
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
} from 'modules/queries/get-task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MockedResponse} from '@apollo/react-testing';

const getWrapper = (key: string, mock: MockedResponse) => {
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={[`/${key}`]}>
      <Route path="/:key">
        <MockedApolloProvider mocks={[mock]}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </MockedApolloProvider>
      </Route>
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Details />', () => {
  it('should render completed task details', async () => {
    render(<Details />, {wrapper: getWrapper('0', mockGetTaskCompleted)});

    expect(await screen.findByText('My Completed Task')).toBeInTheDocument();
    expect(await screen.findByText('Cool Workflow')).toBeInTheDocument();
    expect(await screen.getByTestId('assignee')).toHaveTextContent(
      'Jules Verne',
    );
    expect(
      await screen.findByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(/2020-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
  });

  it('should render unclaimed task details', async () => {
    render(<Details />, {wrapper: getWrapper('1', mockGetTaskUnclaimed)});

    expect(await screen.findByText('Unclaimed Task')).toBeInTheDocument();
    expect(await screen.findByText('Nice Workflow')).toBeInTheDocument();
    expect(await screen.getByTestId('assignee')).toHaveTextContent('--');
    expect(
      await screen.findByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(await screen.queryByText('Completion Time')).not.toBeInTheDocument();
  });
});
