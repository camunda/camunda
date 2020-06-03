/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {Header} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {login} from 'modules/stores/login';
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {mockGetHeaderUser} from 'modules/queries/get-header-user';

const historyMock = createMemoryHistory();
const Wrapper: React.FC = ({children}) => (
  <MockedApolloProvider mocks={[mockGetHeaderUser]}>
    <Router history={historyMock}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </Router>
  </MockedApolloProvider>
);

jest.mock('modules/stores/login');

describe('<Header />', () => {
  it('should render header', async () => {
    render(<Header />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText('Zeebe Tasklist')).toBeInTheDocument();
    expect(await screen.findByText('Demo user')).toBeInTheDocument();
    expect(screen.getByTestId('logo')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should navigate to home page when brand label is clicked', async () => {
    render(<Header />, {
      wrapper: Wrapper,
    });
    await screen.findByText('Demo user');

    fireEvent.click(screen.getByText('Zeebe Tasklist'));

    expect(historyMock.location.pathname).toBe('/');
  });

  it('should handle logout', async () => {
    render(<Header />, {
      wrapper: Wrapper,
    });

    fireEvent.click(await screen.findByText('Demo user'));
    fireEvent.click(screen.getByText('Logout'));

    expect(login.handleLogout).toHaveBeenCalled();
  });
});
