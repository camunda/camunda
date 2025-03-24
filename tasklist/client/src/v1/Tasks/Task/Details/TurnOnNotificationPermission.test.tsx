/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'common/testing/testing-library';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {getStateLocally, storeStateLocally} from 'common/local-storage';

describe('<TurnOnNotificationPermission/>', () => {
  it('when no decision is made, should show a dialog about enabling notifications', () => {
    vi.stubGlobal('Notification', {permission: 'default'});

    render(<TurnOnNotificationPermission />);

    const dialog = screen.getByRole('status', {
      name: /^Don't miss new assignments$/i,
    });

    expect(dialog).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        'Turn on notifications in your browser to get notified when new tasks are assigned to you.',
      ),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', {name: /^Turn on notifications$/i}),
    ).toBeInTheDocument();
  });

  it('when notifications are allowed, should not show a dialog about enabling notifications', () => {
    vi.stubGlobal('Notification', {permission: 'granted'});

    render(<TurnOnNotificationPermission />);

    expect(
      screen.queryByRole('status', {
        name: /^Don't miss new assignments$/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('when notifications are not allowed, should not show a dialog about enabling notifications', () => {
    vi.stubGlobal('Notification', {permission: 'denied'});

    render(<TurnOnNotificationPermission />);

    expect(
      screen.queryByRole('status', {
        name: /^Don't miss new assignments$/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should save user selection on localStorage', async () => {
    vi.stubGlobal('Notification', {permission: 'default'});

    const {user} = render(<TurnOnNotificationPermission />);

    expect(
      screen.getByRole('status', {
        name: /^Don't miss new assignments$/i,
      }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close notification/i}));

    expect(
      screen.queryByRole('status', {
        name: /^Don't miss new assignments$/i,
      }),
    ).not.toBeInTheDocument();
    expect(getStateLocally('areNativeNotificationsEnabled')).toBe(false);
  });

  it('should respect localStorage selection', () => {
    vi.stubGlobal('Notification', {permission: 'default'});
    storeStateLocally('areNativeNotificationsEnabled', false);

    render(<TurnOnNotificationPermission />);

    expect(
      screen.queryByRole('status', {
        name: /^Don't miss new assignments$/i,
      }),
    ).not.toBeInTheDocument();
  });
});
