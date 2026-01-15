/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {render, screen, waitFor} from 'modules/testing-library';
import {processesStore} from 'modules/stores/processes/processes.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {BatchModificationSummaryModal} from './index';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  mockProcessDefinitions,
  mockProcessStatisticsV2,
  mockProcessXML,
} from 'modules/testUtils';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockModifyProcessInstancesBatchOperation} from 'modules/mocks/api/v2/processes/mockModifyProcessInstancesBatchOperation';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {notificationsStore} from 'modules/stores/notifications';

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

const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
  ({children}) => {
    useEffect(() => {
      return () => {
        processesStore.reset();
        batchModificationStore.reset();
        processInstancesSelectionStore.reset();
      };
    });

    return (
      <ProcessDefinitionKeyContext.Provider value={'123'}>
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter
            initialEntries={[
              `${Paths.processes()}?process=bigVarProcess&version=1&flowNodeId=ServiceTask_0kt6c5i`,
            ]}
          >
            {children}
            <button
              onClick={async () => {
                await processesStore.fetchProcesses();
                batchModificationStore.enable();
                batchModificationStore.selectTargetElement('StartEvent_1');
              }}
            >
              init
            </button>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  },
);

describe('BatchModificationSummaryModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatisticsV2);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    mockModifyProcessInstancesBatchOperation().withSuccess({
      batchOperationKey: 'mock-modify-123',
      batchOperationType: 'MODIFY_PROCESS_INSTANCE',
    });

    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  it('should render batch modification summary', async () => {
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatisticsV2);

    const {user} = render(
      <BatchModificationSummaryModal setOpen={() => {}} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /init/i}));

    expect(
      await screen.findByText(
        /Planned modifications for "Big variable process". Click "Apply" to proceed./i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('cell', {name: /batch move/i})).toBeInTheDocument();
    expect(await screen.findByRole('cell', {name: /^1$/})).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {
        name: /Service Task 1 --> Start Event 1/i,
      }),
    ).toBeInTheDocument();
  });

  it('should apply batch operation', async () => {
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatisticsV2);

    const {user} = render(
      <BatchModificationSummaryModal setOpen={() => {}} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByRole('button', {name: /apply/i})).toBeDisabled();

    await user.click(screen.getByRole('button', {name: /init/i}));
    await waitFor(() =>
      expect(screen.getByRole('button', {name: /apply/i})).toBeEnabled(),
    );

    await user.click(screen.getByRole('button', {name: /apply/i}));

    await waitFor(() => {
      expect(batchModificationStore.state.isEnabled).toBe(false);
    });

    expect(tracking.track).toHaveBeenCalledWith({
      eventName: 'batch-operation',
      operationType: 'MODIFY_PROCESS_INSTANCE',
    });
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'success',
      }),
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);
  });

  it('should close the modal when cancel button is clicked', async () => {
    const setOpenMock = vi.fn();

    const {user} = render(
      <BatchModificationSummaryModal setOpen={setOpenMock} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(setOpenMock).toHaveBeenCalledWith(false);
  });

  it('should handle batch operation error', async () => {
    mockModifyProcessInstancesBatchOperation().withServerError(403);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    const {user} = render(
      <BatchModificationSummaryModal setOpen={() => {}} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /init/i}));
    await waitFor(() =>
      expect(screen.getByRole('button', {name: /apply/i})).toBeEnabled(),
    );

    await user.click(screen.getByRole('button', {name: /apply/i}));
  });

  it('should display correct move instruction format', async () => {
    const {user} = render(
      <BatchModificationSummaryModal setOpen={() => {}} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /init/i}));
    await waitFor(() =>
      expect(screen.getByRole('button', {name: /apply/i})).toBeEnabled(),
    );

    expect(
      await screen.findByRole('cell', {
        name: /Service Task 1 --> Start Event 1/i,
      }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /apply/i}));

    await waitFor(() => {
      expect(batchModificationStore.state.isEnabled).toBe(false);
    });
  });

  it('should handle selected process instances for batch operations', async () => {
    processInstancesSelectionStore.selectProcessInstance('12345');
    processInstancesSelectionStore.selectProcessInstance('67890');

    const {user} = render(
      <BatchModificationSummaryModal setOpen={() => {}} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /init/i}));
    await waitFor(() =>
      expect(screen.getByRole('button', {name: /apply/i})).toBeEnabled(),
    );

    await user.click(screen.getByRole('button', {name: /apply/i}));

    await waitFor(() => {
      expect(batchModificationStore.state.isEnabled).toBe(false);
    });

    expect(
      processInstancesSelectionStore.state.selectedProcessInstanceIds,
    ).toContain('12345');
    expect(
      processInstancesSelectionStore.state.selectedProcessInstanceIds,
    ).toContain('67890');
  });
});
