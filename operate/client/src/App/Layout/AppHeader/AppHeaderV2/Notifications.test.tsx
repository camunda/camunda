/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Notifications} from './Notifications';
import {act, render, screen} from 'modules/testing-library';
import {useState} from 'react';
import {
  C3NotificationContext,
  type C3NotificationContextValue,
} from '@camunda/camunda-composite-components/lib/esm/src/components/c3-navigation/c3-notification-provider/c3-notification-provider.js';

vi.mock(
  '@camunda/camunda-composite-components/lib/esm/src/components/c3-navigation/c3-notification-provider/c3-notification-provider.js',
  async (importActual) => ({
    ...(await importActual()),
    default: ({children}: {children?: React.ReactNode}) => children,
  }),
);

describe('Notifications', () => {
  it('should dismiss all notifications', async () => {
    const dismissAll = vi.fn();
    const notifications = [
      {
        uuid: 'notification-1',
        timestamp: Date.now(),
        source: 'console',
        type: 'cluster',
        title: 'Cluster updated',
        description: 'The cluster update finished.',
        state: 'read',
      },
    ];
    const contextValue: C3NotificationContextValue = {
      enabled: true,
      isFetching: false,
      notifications,
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
      dismiss: vi.fn(),
      dismissAll,
      analytics: vi.fn(),
    };
    const {user} = render(
      <C3NotificationContext.Provider value={contextValue}>
        <Notifications />
      </C3NotificationContext.Provider>,
    );

    await user.click(
      screen.getByRole('button', {name: 'Notifications, none unread'}),
    );
    await user.click(screen.getByRole('button', {name: 'Dismiss all'}));

    expect(dismissAll).toHaveBeenCalledWith(notifications);
  });

  it('should preserve unread markers while the panel is open', async () => {
    const NotificationsHarness: React.FC = () => {
      const [notifications, setNotifications] = useState([
        {
          uuid: 'notification-1',
          timestamp: Date.now(),
          source: 'console',
          type: 'cluster',
          title: 'Cluster updated',
          description: 'The cluster update finished.',
          state: 'new',
        },
      ]);
      const contextValue: C3NotificationContextValue = {
        enabled: true,
        isFetching: false,
        notifications,
        markAsRead: vi.fn(),
        markAllAsRead: () => {
          setNotifications((current) =>
            current.map((notification) => ({
              ...notification,
              state: 'read',
            })),
          );
        },
        dismiss: vi.fn(),
        dismissAll: vi.fn(),
        analytics: vi.fn(),
      };

      return (
        <C3NotificationContext.Provider value={contextValue}>
          <Notifications />
        </C3NotificationContext.Provider>
      );
    };
    const {user} = render(<NotificationsHarness />);

    await user.click(
      screen.getByRole('button', {name: 'Notifications, 1 unread'}),
    );

    expect(
      screen
        .getByText('Cluster updated')
        .closest('[data-slot="notification-item"]'),
    ).toHaveAttribute('data-read', 'false');
  });

  it('should mark notifications received while open as read when closing', async () => {
    const markAllAsRead = vi.fn();
    let receiveNotification: () => void = () => undefined;
    const NotificationsHarness: React.FC = () => {
      const [notifications, setNotifications] = useState<
        C3NotificationContextValue['notifications']
      >([]);
      receiveNotification = () =>
        setNotifications([
          {
            uuid: 'notification-1',
            timestamp: Date.now(),
            source: 'console',
            type: 'cluster',
            title: 'Cluster updated',
            description: 'The cluster update finished.',
            state: 'new',
          },
        ]);
      const contextValue: C3NotificationContextValue = {
        enabled: true,
        isFetching: false,
        notifications,
        markAsRead: vi.fn(),
        markAllAsRead,
        dismiss: vi.fn(),
        dismissAll: vi.fn(),
        analytics: vi.fn(),
      };

      return (
        <C3NotificationContext.Provider value={contextValue}>
          <Notifications />
        </C3NotificationContext.Provider>
      );
    };
    const {user} = render(<NotificationsHarness />);

    await user.click(
      screen.getByRole('button', {name: 'Notifications, none unread'}),
    );
    act(receiveNotification);
    await user.click(screen.getByRole('button', {name: 'Close'}));

    expect(markAllAsRead).toHaveBeenCalledWith([
      expect.objectContaining({uuid: 'notification-1'}),
    ]);
  });
});
