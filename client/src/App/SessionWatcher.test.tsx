/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {authenticationStore} from 'modules/stores/authentication';
import {Link, MemoryRouter} from 'react-router-dom';
import {SessionWatcher} from './SessionWatcher';

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
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={initialEntries}>
      {children}
      <Link to="/other-route">get out</Link>
    </MemoryRouter>
  );

  return Wrapper;
}

describe('<SessionWatcher />', () => {
  afterEach(() => {
    authenticationStore.reset();
    mockDisplayNotification.mockReset();
    mockRemoveNotification.mockReset();
  });

  it('should remove a notification', async () => {
    render(<SessionWatcher />, {
      wrapper: getWrapper(['/foo']),
    });

    authenticationStore.setUser({
      displayName: 'Jon',
      canLogout: true,
      permissions: ['read', 'write'],
      userId: 'jon',
    });
    authenticationStore.expireSession();

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
        headline: 'Session expired',
      })
    );

    authenticationStore.setUser({
      displayName: 'Jon Doe',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'jon',
    });

    await waitFor(() => expect(mockRemoveNotification).toHaveBeenCalled());
  });

  it('should show a notification', async () => {
    render(<SessionWatcher />, {
      wrapper: getWrapper(),
    });

    authenticationStore.setUser({
      displayName: 'Jon',
      canLogout: true,
      permissions: [],
      userId: 'jon',
    });
    authenticationStore.expireSession();

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('info', {
        headline: 'Session expired',
      })
    );
  });

  it('should not show a notification', async () => {
    render(<SessionWatcher />, {
      wrapper: getWrapper(['/login']),
    });

    authenticationStore.setUser({
      displayName: 'Jon',
      canLogout: true,
      permissions: [],
      userId: 'jon',
    });
    authenticationStore.expireSession();

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('info', {
      headline: 'Session expired',
    });

    userEvent.click(screen.getByText(/get out/i));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenNthCalledWith(1, 'info', {
        headline: 'Session expired',
      })
    );
  });
});
