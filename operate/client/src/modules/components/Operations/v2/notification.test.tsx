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
import {Operations} from '.';
import {INSTANCE, getWrapper} from './mocks';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {instance} from 'modules/mocks/instance';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

describe('Operations - Notification', () => {
  beforeEach(() => {
    // Mock individual process instance fetch triggered by router
    mockFetchProcessInstance().withSuccess({
      ...instance,
      id: '1',
      processId: '1',
      hasActiveOperation: false,
    });
  });

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
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent('processes/1');
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
    expect(screen.getByTestId('pathname')).toHaveTextContent('processes/1');
  });
});
