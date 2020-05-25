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

const historyMock = createMemoryHistory();
const Wrapper: React.FC = ({children}) => (
  <Router history={historyMock}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </Router>
);

jest.mock('modules/stores/login');

describe('<Header />', () => {
  const historyMock = createMemoryHistory();

  it('should render header', () => {
    render(<Header />, {
      wrapper: Wrapper,
    });
    expect(screen.getByText('Zeebe Tasklist')).toBeInTheDocument();
    expect(screen.getByText('Demo user')).toBeInTheDocument();
    expect(screen.getByTestId('logo')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should navigate to home page when brand label is clicked', () => {
    render(<Header />, {
      wrapper: Wrapper,
    });
    fireEvent.click(screen.getByText('Zeebe Tasklist'));
    expect(historyMock.location.pathname).toBe('/');
  });

  it('should handle logout', () => {
    render(<Header />, {
      wrapper: Wrapper,
    });
    fireEvent.click(screen.getByText('Demo user'));
    fireEvent.click(screen.getByText('Logout'));

    expect(login.handleLogout).toHaveBeenCalled();
  });
});
