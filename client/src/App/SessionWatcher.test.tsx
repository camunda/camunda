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

const mockRemoveNotification = jest.fn();
const mockDisplayNotification = jest.fn(() => ({
  remove: mockRemoveNotification,
}));
jest.mock('modules/notifications', () => ({
  useNotifications: () => {
    return {
      displayNotification: mockDisplayNotification,
    };
  },
}));

function getWrapper(initialEntries = ['/']) {
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
  afterEach(() => {
    mockDisplayNotification.mockReset();
    mockRemoveNotification.mockReset();
  });

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
      });
    });

    act(() => {
      authenticationStore.expireSession();
    });

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
        headline: 'Session expired',
      })
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
      });
    });

    await waitFor(() => expect(mockRemoveNotification).toHaveBeenCalled());
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
      });
    });

    act(() => {
      authenticationStore.expireSession();
    });

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
        headline: 'Session expired',
      })
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
      });
    });

    act(() => {
      authenticationStore.expireSession();
    });

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('info', {
      headline: 'Session expired',
    });

    await user.click(screen.getByText(/get out/i));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenNthCalledWith(1, 'info', {
        headline: 'Session expired',
      })
    );
  });
});
