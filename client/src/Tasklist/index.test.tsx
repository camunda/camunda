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
import {MockedResponse} from '@apollo/client/testing';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockGetAllOpenTasks, mockGetUnclaimed} from 'modules/queries/get-tasks';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';

import {Tasklist} from './index';

const getWrapper = (mock: MockedResponse[] = []): React.FC => ({children}) => {
  return (
    <MockedApolloProvider mocks={mock}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </MemoryRouter>
    </MockedApolloProvider>
  );
};

describe('<Tasklist />', () => {
  it('should load tasks', async () => {
    render(<Tasklist />, {
      wrapper: getWrapper([
        mockGetAllOpenTasks,
        mockGetCurrentUser,
        mockGetUnclaimed,
        mockGetUnclaimed,
      ]),
    });

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeDisabled();
    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeEnabled();
    expect(
      screen.queryByTestId('tasks-loading-overlay'),
    ).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', {name: /filter/i}), {
      target: {
        value: 'unclaimed',
      },
    });

    expect(screen.getByTestId('tasks-loading-overlay')).toBeInTheDocument();
    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeDisabled();

    await waitForElementToBeRemoved(
      screen.getByTestId('tasks-loading-overlay'),
    );

    expect(
      screen.getByRole('combobox', {
        name: /filter/i,
      }),
    ).toBeEnabled();
    expect(
      screen.queryByTestId('tasks-loading-overlay'),
    ).not.toBeInTheDocument();
  });
});
