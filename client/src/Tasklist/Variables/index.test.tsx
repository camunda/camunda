/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {MockedResponse} from '@apollo/react-testing';
import {render, screen} from '@testing-library/react';
import {Route, MemoryRouter} from 'react-router-dom';

import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {
  mockTaskWithVariables,
  mockTaskWithoutVariables,
} from 'modules/queries/get-variables';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Variables} from './';

const getWrapper = (id: string, mock: MockedResponse) => {
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={[`/${id}`]}>
      <Route path="/:id">
        <MockedApolloProvider mocks={[mock]}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </MockedApolloProvider>
      </Route>
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Variables />', () => {
  it('should render with variables', async () => {
    render(<Variables />, {
      wrapper: getWrapper('0', mockTaskWithVariables),
    });

    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('myVar')).toBeInTheDocument();
    expect(screen.getByText('0001')).toBeInTheDocument();
    expect(screen.getByText('isCool')).toBeInTheDocument();
    expect(screen.getByText('yes')).toBeInTheDocument();
  });

  it('should render with empty message', async () => {
    render(<Variables />, {
      wrapper: getWrapper('1', mockTaskWithoutVariables),
    });

    expect(
      await screen.findByText('Task has no variables.'),
    ).toBeInTheDocument();
    expect(
      await screen.queryByTestId('variables-table'),
    ).not.toBeInTheDocument();
  });
});
