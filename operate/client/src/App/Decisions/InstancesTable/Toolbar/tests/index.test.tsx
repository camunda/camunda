/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {Toolbar} from '../index';
import {MemoryRouter} from 'react-router-dom';
import {decisionInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockDeleteDecisionInstancesBatchOperation} from 'modules/mocks/api/v2/decisionInstances/deleteDecisionInstancesBatchOperation';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {tracking} from 'modules/tracking';

type Props = {
  children?: React.ReactNode;
};

vi.mock('modules/feature-flags', () => ({
  IS_DELETE_DI_BATCH_OPERATION_ENABLED: true,
}));

vi.mock('modules/tracking', () => ({
  tracking: {
    track: vi.fn(),
  },
}));

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const Wrapper = ({children}: Props) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={['/decisions?evaluated=true&failed=true']}>
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<Toolbar />', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockDeleteDecisionInstancesBatchOperation().withSuccess({
      batchOperationKey: 'delete-operation-123',
      batchOperationType: 'DELETE_DECISION_INSTANCE',
    });
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    decisionInstancesSelectionStore.setRuntime({
      totalCount: 2,
      visibleIds: ['1', '2'],
    });
    decisionInstancesSelectionStore.select('1');
    decisionInstancesSelectionStore.select('2');
  });

  afterEach(() => {
    vi.resetAllMocks();
    decisionInstancesSelectionStore.reset();
  });

  it('should not display toolbar if selected count is 0', () => {
    render(<Toolbar selectedCount={0} />, {wrapper: Wrapper});

    expect(screen.queryByText(/items selected/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Delete'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Discard'}),
    ).not.toBeInTheDocument();
  });

  it('should display toolbar with Delete button', () => {
    render(<Toolbar selectedCount={1} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByRole('button', {name: 'Delete'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Discard'})).toBeInTheDocument();
    expect(screen.getByText('1 item selected')).toBeInTheDocument();
  });

  it('should perform delete batch operation successfully', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const trackSpy = vi.spyOn(tracking, 'track');

    const {user} = render(<Toolbar selectedCount={2} />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: 'Delete'}));

    const modal = screen.getByRole('dialog');
    expect(
      within(modal).getByText(
        /2 instances selected for delete operation\. This permanently deletes/i,
      ),
    ).toBeInTheDocument();

    await user.click(within(modal).getByRole('button', {name: /delete/i}));

    vi.runOnlyPendingTimers();

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-operation',
      operationType: 'DELETE_DECISION_INSTANCE',
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'success',
      }),
    );
    expect(notificationsStore.displayNotification).not.toHaveBeenCalledWith(
      expect.objectContaining({kind: 'error'}),
    );

    vi.useRealTimers();
  });
});
