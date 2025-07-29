/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor} from '@testing-library/react';
import {render} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {notificationsStore} from 'modules/stores/notifications';
import {ResolveIncident} from './ResolveIncident';
import {mockCreateIncidentResolutionBatchOperation} from 'modules/mocks/api/v2/processInstances/createIncidentResolutionBatchOperation';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockBatchOperation = {
  batchOperationKey: '1234',
  batchOperationType: 'RESOLVE_INCIDENT',
  startDate: '2020-02-06T14:37:29.699+0100',
  operationsFailedCount: 0,
  operationsTotalCount: 1,
  operationsCompletedCount: 0,
  state: 'ACTIVE',
} as const;

describe('Resolve Incident component', () => {
  it('should resolve incident on click', async () => {
    mockCreateIncidentResolutionBatchOperation().withSuccess({});
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    const {user} = render(
      <ResolveIncident processInstanceKey="809213809132809" />,
      {
        wrapper: getWrapper(),
      },
    );

    vi.useFakeTimers({shouldAdvanceTime: true});
    await user.click(screen.getByRole('button', {name: /retry instance/i}));

    // Expect button to be disabled right after operation was created
    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /retry instance/i}),
      ).toBeDisabled(),
    );

    // Expect button to be disabled when operation is pending
    mockQueryBatchOperations().withSuccess({
      items: [mockBatchOperation],
      page: {totalItems: 1},
    });
    vi.runOnlyPendingTimersAsync();
    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /retry instance/i}),
      ).toBeDisabled(),
    );

    // Expect button to be enabled when operation was successful
    mockQueryBatchOperations().withSuccess({
      items: [{...mockBatchOperation, state: 'COMPLETED'}],
      page: {totalItems: 1},
    });
    vi.runOnlyPendingTimersAsync();
    await waitFor(() =>
      expect(
        screen.getByRole('button', {name: /retry instance/i}),
      ).toBeEnabled(),
    );
    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();

    vi.useRealTimers();
  });

  it('should show notification on server error', async () => {
    mockCreateIncidentResolutionBatchOperation().withServerError();

    const {user} = render(
      <ResolveIncident processInstanceKey="809213809132809" />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(screen.getByRole('button', {name: /retry instance/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to retry process instance',
        isDismissable: true,
        subtitle: 'Internal Server Error',
      }),
    );
  });
});
