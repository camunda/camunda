/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {createBatchOperation} from 'modules/testUtils';
import {Operations} from '../index';
import {INSTANCE, Wrapper} from './mocks';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

describe('Operations - Notification', () => {
  it('should not display notification and redirect if delete operation is performed on instances page', async () => {
    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'COMPLETED',
        }}
        onError={() => {}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    await user.click(screen.getByRole('button', {name: /Delete Instance/}));
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();

    mockApplyOperation().withSuccess(createBatchOperation());

    const confirmDeletionModal = screen.getByTestId('confirm-deletion-modal');

    const withinConfirmDeletionModal = within(confirmDeletionModal);

    await user.click(
      withinConfirmDeletionModal.getByRole('button', {name: /delete/i}),
    );

    expect(
      screen.queryByText(/About to delete Instance/),
    ).not.toBeInTheDocument();

    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
  });
});
