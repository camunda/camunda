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

import {render, screen} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {groupedProcessesMock} from 'modules/testUtils';
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';
import {batchModificationStore} from 'modules/stores/batchModification';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {BatchModificationNotification} from '.';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockProcessStatisticsWithActiveAndIncidents} from 'modules/mocks/mockProcessStatistics';

jest.mock('modules/utils/bpmn');

const notificationText1 =
  'Please select where you want to move the selected instances on the diagram.';

const notificationText2 =
  'Modification scheduled: Move 4 instances from “userTask” to “endEvent”. Press “Apply Modification” button to confirm.';

function getWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processStatisticsBatchModificationStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>
        {children}
        <button
          onClick={() => {
            processStatisticsBatchModificationStore.fetchProcessStatistics();
          }}
        >
          Fetch modification statistics
        </button>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(
      mockProcessStatisticsWithActiveAndIncidents,
    );
  });

  it('should initially render batch modification notification', async () => {
    render(<BatchModificationNotification />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(notificationText1)).toBeInTheDocument();
  });

  it('should initially render batch modification notification', async () => {
    const {user} = render(
      <BatchModificationNotification
        sourceFlowNodeId="userTask"
        targetFlowNodeId="endEvent"
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      screen.getByRole('button', {name: /Fetch modification statistics/i}),
    );

    expect(await screen.findByText(notificationText2)).toBeInTheDocument();
    expect(screen.queryByText(notificationText1)).not.toBeInTheDocument();
  });

  it('should render Undo button if target is selected', async () => {
    const undoMock = jest.fn();

    const {user} = render(
      <BatchModificationNotification
        sourceFlowNodeId="userTask"
        targetFlowNodeId="endEvent"
        onUndoClick={undoMock}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      screen.getByRole('button', {name: /Fetch modification statistics/i}),
    );

    expect(await screen.findByText(notificationText2)).toBeInTheDocument();
    expect(screen.queryByText(notificationText1)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /undo/i}));

    expect(undoMock).toHaveBeenCalled();
  });

  it('should not render Undo button if no target is selected', async () => {
    render(<BatchModificationNotification sourceFlowNodeId="userTask" />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText(notificationText1)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /undo/i}),
    ).not.toBeInTheDocument();
  });
});
