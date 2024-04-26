/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {render, screen, waitFor} from 'modules/testing-library';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';
import {BatchModificationSummaryModal} from '.';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import * as hooks from 'App/Processes/ListView/InstancesTable/useOperationApply';
import {tracking} from 'modules/tracking';

jest.mock('App/Processes/ListView/InstancesTable/useOperationApply');

const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
  ({children}) => {
    useEffect(() => {
      return () => {
        processesStore.reset();
        processXmlStore.reset();
        batchModificationStore.reset();
        processStatisticsBatchModificationStore.reset();
      };
    });

    return (
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
            processStatisticsBatchModificationStore.setStatistics(
              mockProcessStatistics,
            );
            batchModificationStore.enable();
            batchModificationStore.selectTargetFlowNode('StartEvent_1');
          }}
        >
          init
        </button>
      </MemoryRouter>
    );
  },
);

describe('BatchModificationSummaryModal', () => {
  it('should render batch modification summary', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

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
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess(mockProcessXML);

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
});
