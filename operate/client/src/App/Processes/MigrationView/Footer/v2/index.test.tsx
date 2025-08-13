/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {Footer} from './index';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {tracking} from 'modules/tracking';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockMigrateProcessInstancesBatchOperation} from 'modules/mocks/api/v2/processes/migrateProcessInstancesBatchOperation';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {notificationsStore} from 'modules/stores/notifications';

type Props = {
  children?: React.ReactNode;
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

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');
    return processInstanceMigrationStore.reset;
  }, []);
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>
        {children}
        <button
          onClick={() => {
            processInstanceMigrationStore.updateFlowNodeMapping({
              sourceId: 'task1',
              targetId: 'task2',
            });
          }}
        >
          map element
        </button>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('Footer', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockMigrateProcessInstancesBatchOperation().withSuccess({
      batchOperationKey: 'migrate-operation-123',
      batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
    });

    mockQueryBatchOperations().withSuccess({
      items: [
        {
          batchOperationKey: 'migrate-operation-123',
          batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
          startDate: '2021-02-20T18:31:18.625+0100',
          endDate: '2023-11-22T09:03:29.564+0100',
          state: 'COMPLETED',
          operationsTotalCount: 2,
          operationsCompletedCount: 2,
          operationsFailedCount: 0,
        },
      ],
      page: {totalItems: 1},
    });
  });

  afterEach(() => {
    vi.resetAllMocks();
    vi.clearAllTimers();
    vi.useRealTimers();
    processInstanceMigrationStore.reset();
    panelStatesStore.reset();
  });
  it('should render correct buttons in each step', async () => {
    const {user} = render(<Footer />, {wrapper: Wrapper});

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /map element/i}));

    await user.click(screen.getByRole('button', {name: 'Next'}));

    expect(
      screen.queryByRole('button', {name: 'Next'}),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Back'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Confirm'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Back'}));

    expect(screen.getByRole('button', {name: 'Next'})).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Back'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Confirm'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Exit migration'}),
    ).toBeInTheDocument();
  });

  it('should display confirmation modal on exit migration click', async () => {
    const {user} = render(<Footer />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: 'Exit migration'}));

    expect(
      screen.getByText(
        /You are about to leave ongoing migration, all planned mapping\/s will be discarded./,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Click “Exit” to proceed./)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(
      screen.queryByText(
        /You are about to leave ongoing migration, all planned mapping\/s will be discarded./,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Click “Exit” to proceed./),
    ).not.toBeInTheDocument();

    expect(processInstanceMigrationStore.isEnabled).toBe(true);

    await user.click(screen.getByRole('button', {name: 'Exit migration'}));

    await user.click(screen.getByRole('button', {name: 'danger Exit'}));
    expect(processInstanceMigrationStore.isEnabled).toBe(false);
  });

  it('should track confirm button click', async () => {
    const trackSpy = vi.spyOn(tracking, 'track');

    processInstanceMigrationStore.setBatchOperationQuery({
      active: true,
    });
    processInstanceMigrationStore.setTargetProcessDefinitionKey('test-key');

    const {user} = render(<Footer />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /map element/i}));
    await user.click(screen.getByRole('button', {name: /next/i}));
    await user.click(screen.getByRole('button', {name: /confirm/i}));

    const withinModal = within(screen.getByRole('dialog'));
    await user.type(withinModal.getByRole('textbox'), 'MIGRATE');
    await user.click(withinModal.getByRole('button', {name: /confirm/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'process-instance-migration-confirmed',
    });
  });

  it('should perform migration batch operation successfully and expand operations panel', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    processInstanceMigrationStore.setBatchOperationQuery({
      active: true,
      ids: ['1', '2'],
    });
    processInstanceMigrationStore.setTargetProcessDefinitionKey(
      'target-process-key',
    );
    processInstanceMigrationStore.setSourceProcessDefinitionKey(
      'source-process-key',
    );

    const trackSpy = vi.spyOn(tracking, 'track');

    const {user} = render(<Footer />, {wrapper: Wrapper});

    await user.click(screen.getByRole('button', {name: /map element/i}));
    await user.click(screen.getByRole('button', {name: /next/i}));
    await user.click(screen.getByRole('button', {name: /confirm/i}));

    const withinModal = within(screen.getByRole('dialog'));
    await user.type(withinModal.getByRole('textbox'), 'MIGRATE');
    await user.click(withinModal.getByRole('button', {name: /confirm/i}));

    vi.runOnlyPendingTimers();

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-operation',
      operationType: 'MIGRATE_PROCESS_INSTANCE',
    });

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);

    expect(notificationsStore.displayNotification).not.toHaveBeenCalledWith(
      expect.objectContaining({kind: 'error'}),
    );

    vi.useRealTimers();
  });
});
