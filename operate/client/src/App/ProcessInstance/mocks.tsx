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

import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchSequenceFlows} from 'modules/mocks/api/processInstances/sequenceFlows';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockIncidents} from 'modules/mocks/incidents';
import {testData} from './index.setup';
import {mockSequenceFlows} from './TopPanel/index.setup';
import {
  createMultiInstanceFlowNodeInstances,
  createVariable,
} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {
  Route,
  unstable_HistoryRouter as HistoryRouter,
  Routes,
} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {createMemoryHistory} from 'history';
import {LocationLog} from 'modules/utils/LocationLog';
import {
  Selection,
  flowNodeSelectionStore,
} from 'modules/stores/flowNodeSelection';
import {useEffect} from 'react';
import {waitFor} from '@testing-library/react';
import {variablesStore} from 'modules/stores/variables';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');

const mockRequests = (contextPath: string = '') => {
  mockFetchProcessInstance(contextPath).withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessXML(contextPath).withSuccess('');
  mockFetchSequenceFlows(contextPath).withSuccess(mockSequenceFlows);
  mockFetchFlowNodeInstances(contextPath).withSuccess(
    processInstancesMock.level1,
  );
  mockFetchProcessInstanceDetailStatistics(contextPath).withSuccess([
    {
      activityId: 'taskD',
      active: 1,
      incidents: 1,
      completed: 0,
      canceled: 0,
    },
  ]);
  mockFetchVariables(contextPath).withSuccess([createVariable()]);
  mockFetchProcessInstanceIncidents(contextPath).withSuccess({
    ...mockIncidents,
    count: 2,
  });
};

type FlowNodeSelectorProps = {
  selectableFlowNode: Selection;
};

const FlowNodeSelector: React.FC<FlowNodeSelectorProps> = ({
  selectableFlowNode,
}) => (
  <button
    onClick={() => {
      flowNodeSelectionStore.selectFlowNode(selectableFlowNode);
    }}
  >
    {`Select flow node`}
  </button>
);

type Props = {
  children?: React.ReactNode;
};

function getWrapper(options?: {
  initialPath?: string;
  contextPath?: string;
  selectableFlowNode?: Selection;
}) {
  const {
    initialPath = Paths.processInstance('4294980768'),
    contextPath,
    selectableFlowNode,
  } = options ?? {};

  const Wrapper: React.FC<Props> = ({children}) => {
    useEffect(() => {
      return flowNodeSelectionStore.reset;
    }, []);

    return (
      <HistoryRouter
        history={createMemoryHistory({
          initialEntries: [initialPath],
        })}
        basename={contextPath ?? ''}
      >
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
          <Route path={Paths.processes()} element={<>instances page</>} />
          <Route path={Paths.dashboard()} element={<>dashboard page</>} />
        </Routes>
        {selectableFlowNode && (
          <FlowNodeSelector selectableFlowNode={selectableFlowNode} />
        )}
        <LocationLog />
      </HistoryRouter>
    );
  };

  return Wrapper;
}

const waitForPollingsToBeComplete = async () => {
  await waitFor(() => {
    expect(variablesStore.isPollRequestRunning).toBe(false);
    expect(sequenceFlowsStore.isPollRequestRunning).toBe(false);
    expect(processInstanceDetailsStore.isPollRequestRunning).toBe(false);
    expect(incidentsStore.isPollRequestRunning).toBe(false);
    expect(flowNodeInstanceStore.isPollRequestRunning).toBe(false);
    expect(processInstanceDetailsStatisticsStore.isPollRequestRunning).toBe(
      false,
    );
  });
};

export {getWrapper, testData, waitForPollingsToBeComplete};

export {mockRequests};
