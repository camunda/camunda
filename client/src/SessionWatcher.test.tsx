/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter} from 'react-router-dom';
import {render, waitFor} from '@testing-library/react';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {authenticationStore} from 'modules/stores/authentication';
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
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
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
    authenticationStore.reset();
  });

  it('should display notification if session is expired on main page', async () => {
    authenticationStore.activateSession();

    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/'],
      }),
    });
    authenticationStore.disableSession();

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenNthCalledWith(1, 'info', {
        headline: 'Session expired',
      }),
    );
  });

  it('should display notification if session is expired on task detail page', async () => {
    authenticationStore.activateSession();

    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/1234'],
      }),
    });
    authenticationStore.disableSession();
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
    authenticationStore.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();
  });

  it('should display notification on initial login on task detail page', async () => {
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/1234'],
      }),
    });
    authenticationStore.disableSession();
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
    authenticationStore.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();

    // after first login
    authenticationStore.activateSession();
    authenticationStore.disableSession();
    expect(mockDisplayNotification).not.toHaveBeenCalled();
  });
});
