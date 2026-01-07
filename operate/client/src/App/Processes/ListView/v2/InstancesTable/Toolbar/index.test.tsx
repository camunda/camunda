/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Toolbar} from '.';
import {MemoryRouter} from 'react-router-dom';
import {batchModificationStore} from 'modules/stores/batchModification';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {panelStatesStore} from 'modules/stores/panelStates';
import {notificationsStore} from 'modules/stores/notifications';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockCancelProcessInstancesBatchOperation} from 'modules/mocks/api/v2/processes/cancelProcessInstancesBatchOperation';
import {mockResolveProcessInstancesIncidentsBatchOperation} from 'modules/mocks/api/v2/processes/resolveProcessInstancesIncidentsBatchOperation';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
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

    processInstancesSelectionStore.selectProcessInstance('1');
    processInstancesSelectionStore.selectProcessInstance('2');

    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.isAllChecked = false;
  });

  afterEach(() => {
    vi.resetAllMocks();

    batchModificationStore.reset();
    processInstancesSelectionStore.reset();
    processInstancesStore.reset();
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

    processInstancesStore.setProcessInstances({
      filteredProcessInstancesCount: 2,
      processInstances: [
        {
          id: '1',
          processId: 'process-1',
          processName: 'Test Process',
          processVersion: 1,
          startDate: '2023-01-01T00:00:00.000Z',
          endDate: null,
          state: 'ACTIVE',
          bpmnProcessId: 'testProcess',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '<default>',
        },
        {
          id: '2',
          processId: 'process-2',
          processName: 'Test Process',
          processVersion: 1,
          startDate: '2023-01-01T00:00:00.000Z',
          endDate: null,
          state: 'INCIDENT',
          bpmnProcessId: 'testProcess',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '<default>',
        },
      ],
    });

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

    expect(notificationsStore.displayNotification).not.toHaveBeenCalledWith(
      expect.objectContaining({kind: 'error'}),
    );

    vi.useRealTimers();
  });

  it('should perform resolve batch operation successfully', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    processInstancesStore.setProcessInstances({
      filteredProcessInstancesCount: 2,
      processInstances: [
        {
          id: '1',
          processId: 'process-1',
          processName: 'Test Process',
          processVersion: 1,
          startDate: '2023-01-01T00:00:00.000Z',
          endDate: null,
          state: 'ACTIVE',
          bpmnProcessId: 'testProcess',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '<default>',
        },
        {
          id: '2',
          processId: 'process-2',
          processName: 'Test Process',
          processVersion: 1,
          startDate: '2023-01-01T00:00:00.000Z',
          endDate: null,
          state: 'INCIDENT',
          bpmnProcessId: 'testProcess',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '<default>',
        },
      ],
    });

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

    expect(notificationsStore.displayNotification).not.toHaveBeenCalledWith(
      expect.objectContaining({kind: 'error'}),
    );

    vi.useRealTimers();
  });
});
