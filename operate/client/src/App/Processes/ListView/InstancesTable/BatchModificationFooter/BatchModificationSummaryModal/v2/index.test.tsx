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
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {BatchModificationSummaryModal} from '.';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  groupedProcessesMock,
  mockProcessStatisticsV2,
  mockProcessXML,
} from 'modules/testUtils';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import * as modificationMutation from 'modules/mutations/processInstance/useModifyProcessInstanceBatchOperation';
import {tracking} from 'modules/tracking';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';

vi.mock(
  'modules/mutations/batchOperations/useModifyProcessInstanceBatchOperation',
);

const createMockMutation = (mutateMock: ReturnType<typeof vi.fn>) => ({
  mutate: mutateMock,
  error: null,
  data: undefined,
  reset: vi.fn(),
  isError: false as const,
  isSuccess: false as const,
  isPending: false as const,
  failureCount: 0,
  failureReason: null,
  isPaused: false as const,
  status: 'idle' as const,
  submittedAt: 0,
  variables: undefined,
  context: undefined,
  isIdle: true as const,
  mutateAsync: vi.fn(),
});

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
                batchModificationStore.selectTargetFlowNode('StartEvent_1');
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
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatisticsV2);
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });
  });

  it('should render batch modification summary', async () => {
    const mutateMock = vi.fn();
    vi.spyOn(
      modificationMutation,
      'useModifyProcessInstanceBatchOperation',
    ).mockReturnValue(createMockMutation(mutateMock));
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

    const trackSpy = vi.spyOn(tracking, 'track');
    const mutateMock = vi.fn();
    vi.spyOn(
      modificationMutation,
      'useModifyProcessInstanceBatchOperation',
    ).mockReturnValue(createMockMutation(mutateMock));

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

    expect(batchModificationStore.state.isEnabled).toBe(false);
    expect(mutateMock).toHaveBeenCalledTimes(1);
    expect(mutateMock).toHaveBeenCalledWith({
      moveInstructions: [
        {
          sourceElementId: 'ServiceTask_0kt6c5i',
          targetElementId: 'StartEvent_1',
        },
      ],
      filter: expect.any(Object),
    });
    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-move-modification-apply-button-clicked',
    });
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
    const mutateMock = vi.fn();

    vi.spyOn(
      modificationMutation,
      'useModifyProcessInstanceBatchOperation',
    ).mockImplementation((options) => {
      setTimeout(() => {
        options?.onError?.(
          new Error('403 Forbidden'),
          {moveInstructions: [], filter: {}},
          undefined,
        );
      }, 0);

      return {
        mutate: mutateMock,
        error: null,
        data: undefined,
        reset: vi.fn(),
        isError: false,
        isSuccess: false,
        isPending: false,
        failureCount: 0,
        failureReason: null,
        isPaused: false,
        status: 'idle' as const,
        submittedAt: 0,
        variables: undefined,
        context: undefined,
        isIdle: true,
        mutateAsync: vi.fn(),
      };
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

    expect(mutateMock).toHaveBeenCalledTimes(1);
  });

  it('should display correct move instruction format', async () => {
    // Note: In test environment, filter may be empty but moveInstructions should be correct

    const mutateMock = vi.fn();
    vi.spyOn(
      modificationMutation,
      'useModifyProcessInstanceBatchOperation',
    ).mockReturnValue(createMockMutation(mutateMock));

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

    expect(mutateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        moveInstructions: [
          {
            sourceElementId: 'ServiceTask_0kt6c5i',
            targetElementId: 'StartEvent_1',
          },
        ],
        filter: expect.any(Object),
      }),
    );
  });

  it('should handle selected process instances for batch operations', async () => {
    processInstancesSelectionStore.selectProcessInstance('12345');
    processInstancesSelectionStore.selectProcessInstance('67890');

    const mutateMock = vi.fn();
    vi.spyOn(
      modificationMutation,
      'useModifyProcessInstanceBatchOperation',
    ).mockReturnValue(createMockMutation(mutateMock));

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

    expect(mutateMock).toHaveBeenCalledWith(
      expect.objectContaining({
        filter: expect.objectContaining({
          processDefinitionKey: {
            $eq: '123',
          },
          processInstanceKey: {
            $in: ['12345', '67890'],
          },
        }),
      }),
    );
  });
});
