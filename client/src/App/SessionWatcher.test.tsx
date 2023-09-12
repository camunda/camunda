/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {Link, MemoryRouter} from 'react-router-dom';
import {SessionWatcher} from './SessionWatcher';
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';
import {Paths} from 'modules/Routes';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => {
  const hideNotificationMock = jest.fn();
  return {
    notificationsStore: {
      hideNotification: hideNotificationMock,
      displayNotification: jest.fn(() => hideNotificationMock),
    },
  };
});

function getWrapper(initialEntries = [Paths.dashboard()]) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return authenticationStore.reset;
    });
    return (
      <MemoryRouter initialEntries={initialEntries}>
        {children}
        <Link to="/other-route">get out</Link>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('<SessionWatcher />', () => {
  it('should remove a notification', async () => {
    render(<SessionWatcher />, {
      wrapper: getWrapper(['/foo']),
    });

    act(() => {
      authenticationStore.setUser({
        displayName: 'Jon',
        canLogout: true,
        permissions: ['read', 'write'],
        userId: 'jon',
        roles: null,
        salesPlanType: null,
        c8Links: {},
        tenants: [],
      });
    });

    act(() => {
      authenticationStore.expireSession();
    });

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'info',
        title: 'Session expired',
      }),
    );

    act(() => {
      authenticationStore.setUser({
        displayName: 'Jon Doe',
        permissions: ['read', 'write'],
        canLogout: true,
        userId: 'jon',
        roles: null,
        salesPlanType: null,
        c8Links: {},
        tenants: [],
      });
    });

    await waitFor(() =>
      expect(notificationsStore.hideNotification).toHaveBeenCalled(),
    );
  });

  it('should show a notification', async () => {
    render(<SessionWatcher />, {
      wrapper: getWrapper(),
    });

    act(() => {
      authenticationStore.setUser({
        displayName: 'Jon',
        canLogout: true,
        permissions: [],
        userId: 'jon',
        roles: null,
        salesPlanType: null,
        c8Links: {},
        tenants: [],
      });
    });

    act(() => {
      authenticationStore.expireSession();
    });

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'info',
        title: 'Session expired',
      }),
    );
  });

  it('should not show a notification', async () => {
    const {user} = render(<SessionWatcher />, {
      wrapper: getWrapper(['/login']),
    });

    act(() => {
      authenticationStore.setUser({
        displayName: 'Jon',
        canLogout: true,
        permissions: [],
        userId: 'jon',
        roles: null,
        salesPlanType: null,
        c8Links: {},
        tenants: [],
      });
    });

    act(() => {
      authenticationStore.expireSession();
    });

    expect(notificationsStore.displayNotification).not.toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'info',
      title: 'Session expired',
    });

    await user.click(screen.getByText(/get out/i));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenNthCalledWith(
        1,
        {
          isDismissable: true,
          kind: 'info',
          title: 'Session expired',
        },
      ),
    );
  });
});
