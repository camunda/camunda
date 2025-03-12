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
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {BatchModificationSummaryModal} from '.';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  groupedProcessesMock,
  mockProcessStatisticsV2,
  mockProcessXML,
} from 'modules/testUtils';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import * as hooks from 'App/Processes/ListView/InstancesTable/useOperationApply';
import {tracking} from 'modules/tracking';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

jest.mock('App/Processes/ListView/InstancesTable/useOperationApply');

const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
  ({children}) => {
    useEffect(() => {
      return () => {
        processesStore.reset();
        processXmlStore.reset();
        batchModificationStore.reset();
      };
    });

    return (
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
              await processXmlStore.fetchProcessXml();
              batchModificationStore.enable();
              batchModificationStore.selectTargetFlowNode('StartEvent_1');
            }}
          >
            init
          </button>
        </MemoryRouter>
      </QueryClientProvider>
    );
  },
);

describe('BatchModificationSummaryModal', () => {
  beforeEach(() => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatisticsV2);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should render batch modification summary', async () => {
    const applyBatchOperationMock = jest.fn();
    jest.spyOn(hooks, 'default').mockImplementation(() => ({
      applyBatchOperation: applyBatchOperationMock,
    }));
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
    const trackSpy = jest.spyOn(tracking, 'track');
    const applyBatchOperationMock = jest.fn();
    jest.spyOn(hooks, 'default').mockImplementation(() => ({
      applyBatchOperation: applyBatchOperationMock,
    }));

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
    expect(applyBatchOperationMock).toHaveBeenCalledTimes(1);
    expect(applyBatchOperationMock).toHaveBeenCalledWith({
      modifications: [
        {
          fromFlowNodeId: 'ServiceTask_0kt6c5i',
          modification: 'MOVE_TOKEN',
          toFlowNodeId: 'StartEvent_1',
        },
      ],
      onSuccess: expect.any(Function),
      operationType: 'MODIFY_PROCESS_INSTANCE',
    });
    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-move-modification-apply-button-clicked',
    });
  });

  it('should close the modal when cancel button is clicked', async () => {
    const setOpenMock = jest.fn();

    const {user} = render(
      <BatchModificationSummaryModal setOpen={setOpenMock} open={true} />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(setOpenMock).toHaveBeenCalledWith(false);
  });
});
