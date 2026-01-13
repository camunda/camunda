/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library.ts';
import {Toolbar} from '../index.tsx';
import {MemoryRouter} from 'react-router-dom';
import {batchModificationStore} from 'modules/stores/batchModification.ts';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2.ts';
import {panelStatesStore} from 'modules/stores/panelStates.ts';
import {notificationsStore} from 'modules/stores/notifications.tsx';
import {variableFilterStore} from 'modules/stores/variableFilter.ts';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient.ts';
import {mockCancelProcessInstancesBatchOperation} from 'modules/mocks/api/v2/processes/cancelProcessInstancesBatchOperation.ts';
import {mockResolveProcessInstancesIncidentsBatchOperation} from 'modules/mocks/api/v2/processes/resolveProcessInstancesIncidentsBatchOperation.ts';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations.ts';
import {tracking} from 'modules/tracking';

type Props = {
  children?: React.ReactNode;
  initialEntries?: string[];
};

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

const Wrapper = ({
  children,
  initialEntries = [
    '/processes?flowNodeId=test-activity&incidents=false&ids=1,2',
  ],
}: Props) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={initialEntries}>
        {children}
        <button onClick={batchModificationStore.enable}>
          Enter batch modification mode
        </button>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<ProcessOperations />', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockCancelProcessInstancesBatchOperation().withSuccess({
      batchOperationKey: 'cancel-operation-123',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
    });
    mockResolveProcessInstancesIncidentsBatchOperation().withSuccess({
      batchOperationKey: 'resolve-operation-456',
      batchOperationType: 'RESOLVE_INCIDENT',
    });
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    processInstancesSelectionStore.init();
    processInstancesSelectionStore.setRuntime({
      totalProcessInstancesCount: 2,
      visibleIds: ['1', '2'],
      visibleRunningIds: ['1', '2'],
    });
    processInstancesSelectionStore.selectProcessInstance('1');
    processInstancesSelectionStore.selectProcessInstance('2');
  });

  afterEach(() => {
    vi.resetAllMocks();

    batchModificationStore.reset();
    processInstancesSelectionStore.reset();
    panelStatesStore.reset();
    variableFilterStore.reset();
  });
  it('should not display toolbar if selected instances count is 0', async () => {
    render(<Toolbar selectedInstancesCount={0} />, {wrapper: Wrapper});

    expect(screen.queryByText(/items selected/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Retry'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Cancel'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Discard'}),
    ).not.toBeInTheDocument();
  });

  it('should display toolbar with action buttons', async () => {
    const {rerender} = render(<Toolbar selectedInstancesCount={1} />, {
      wrapper: Wrapper,
    });

    expect(screen.getAllByRole('button', {name: 'Cancel'}).length).toBe(2);
    expect(screen.getByRole('button', {name: 'Retry'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Discard'})).toBeInTheDocument();
    expect(screen.getByText('1 item selected')).toBeInTheDocument();

    rerender(<Toolbar selectedInstancesCount={10} />);

    expect(screen.getByText('10 items selected')).toBeInTheDocument();
  });

  it('should disable cancel and retry in batch modification mode', async () => {
    const {user} = render(<Toolbar selectedInstancesCount={1} />, {
      wrapper: Wrapper,
    });

    await user.click(
      screen.getByRole('button', {name: /enter batch modification mode/i}),
    );

    expect(
      screen.getByRole('button', {
        description: 'Not available in batch modification mode',
        name: 'Cancel',
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', {
        description: 'Not available in batch modification mode',
        name: 'Retry',
      }),
    ).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Discard'})).toBeEnabled();
  });

  it('should perform cancel batch operation successfully', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const trackSpy = vi.spyOn(tracking, 'track');

    const {user} = render(<Toolbar selectedInstancesCount={2} />, {
      wrapper: Wrapper,
    });

    await user.click(screen.getByTestId('cancel-batch-operation'));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    vi.runOnlyPendingTimers();

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-operation',
      operationType: 'CANCEL_PROCESS_INSTANCE',
    });

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);

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

  it('should perform resolve batch operation successfully', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const trackSpy = vi.spyOn(tracking, 'track');

    const {user} = render(<Toolbar selectedInstancesCount={2} />, {
      wrapper: Wrapper,
    });

    await user.click(screen.getByTestId('retry-batch-operation'));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    vi.runOnlyPendingTimers();

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-operation',
      operationType: 'RESOLVE_INCIDENT',
    });

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);

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
