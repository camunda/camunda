/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {Router} from 'react-router-dom';
import {createMemoryHistory, History} from 'history';
import {render, waitFor} from '@testing-library/react';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {login} from 'modules/stores/login';
import {SessionWatcher} from './SessionWatcher';

const mockDisplayNotification = jest.fn();

jest.mock('modules/notifications', () => ({
  useNotifications: () => {
    return {
      displayNotification: mockDisplayNotification,
    };
  },
}));

type GetWrapperProps = {
  history: History;
};

const getWrapper = ({history}: GetWrapperProps) => {
  const Wrapper: React.FC = ({children}) => (
    <MockThemeProvider>
      <Router history={history}>
        <SessionWatcher />
        {children}
      </Router>
    </MockThemeProvider>
  );

  return Wrapper;
};

describe('SessionWatcher', () => {
  afterEach(() => {
    login.reset();
  });

  it('should display notification if session is expired on main page', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/'],
    });
    login.activateSession();

    render(<div />, {
      wrapper: getWrapper({
        history,
      }),
    });
    login.disableSession();

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenNthCalledWith(1, 'info', {
        headline: 'Session expired',
      }),
    );
  });

  it('should display notification if session is expired on task detail page', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/1234'],
    });
    login.activateSession();

    render(<div />, {
      wrapper: getWrapper({
        history,
      }),
    });
    login.disableSession();
    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenNthCalledWith(1, 'info', {
        headline: 'Session expired',
      }),
    );
  });

  it('should not display notification on initial login on main page', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/'],
    });

    render(<div />, {
      wrapper: getWrapper({
        history,
      }),
    });
    login.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();
  });

  it('should display notification on initial login on task detail page', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/1234'],
    });

    render(<div />, {
      wrapper: getWrapper({
        history,
      }),
    });
    login.disableSession();
    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenNthCalledWith(1, 'info', {
        headline: 'Session expired',
      }),
    );
  });

  it('should not display notification on login page', async () => {
    const history = createMemoryHistory({
      initialEntries: ['/login'],
    });

    render(<div />, {
      wrapper: getWrapper({
        history,
      }),
    });

    // initial state
    login.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();

    // after first login
    login.activateSession();
    login.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();
  });
});
