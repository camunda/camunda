/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {MemoryRouter} from 'react-router-dom';
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
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'];
};

const getWrapper = ({initialEntries}: GetWrapperProps) => {
  const Wrapper: React.FC = ({children}) => (
    <MockThemeProvider>
      <MemoryRouter initialEntries={initialEntries}>
        <SessionWatcher />
        {children}
      </MemoryRouter>
    </MockThemeProvider>
  );

  return Wrapper;
};

describe('SessionWatcher', () => {
  afterEach(() => {
    login.reset();
  });

  it('should display notification if session is expired on main page', async () => {
    login.activateSession();

    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/'],
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
    login.activateSession();

    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/1234'],
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
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/'],
      }),
    });
    login.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();
  });

  it('should display notification on initial login on task detail page', async () => {
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/1234'],
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
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/login'],
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
