/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MemoryRouter} from 'react-router-dom';
import {act, render, waitFor} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {authenticationStore} from 'modules/stores/authentication';
import {SessionWatcher} from './SessionWatcher';
import {notificationsStore} from 'modules/stores/notifications';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
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
    act(() => authenticationStore.reset());
  });

  it('should display notification if session is expired on main page', async () => {
    act(() => authenticationStore.activateSession());

    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/'],
      }),
    });
    act(() => authenticationStore.disableSession());

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenNthCalledWith(
        1,
        {
          kind: 'info',
          title: 'Session expired',
          isDismissable: true,
        },
      ),
    );
  });

  it('should display notification if session is expired on task detail page', async () => {
    act(() => authenticationStore.activateSession());

    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/1234'],
      }),
    });
    act(() => authenticationStore.disableSession());
    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenNthCalledWith(
        1,
        {
          kind: 'info',
          title: 'Session expired',
          isDismissable: true,
        },
      ),
    );
  });

  it('should not display notification on initial login on main page', async () => {
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/'],
      }),
    });
    act(() => authenticationStore.disableSession());
    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
  });

  it('should display notification on initial login on task detail page', async () => {
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/1234'],
      }),
    });
    act(() => authenticationStore.disableSession());
    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenNthCalledWith(
        1,
        {
          kind: 'info',
          title: 'Session expired',
          isDismissable: true,
        },
      ),
    );
  });

  it('should not display notification on login page', async () => {
    render(<div />, {
      wrapper: getWrapper({
        initialEntries: ['/login'],
      }),
    });

    // initial state
    act(() => authenticationStore.disableSession());
    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();

    // after first login
    act(() => authenticationStore.activateSession());
    act(() => authenticationStore.disableSession());
    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
  });
});
