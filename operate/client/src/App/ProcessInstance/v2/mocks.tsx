/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockIncidents} from 'modules/mocks/incidents';
import {testData} from '../index.setup';
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
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {mockFetchProcess} from 'modules/mocks/api/processes/fetchProcess';
import {mockProcess} from '../ProcessInstanceHeader/index.setup';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';
import {noListeners} from 'modules/mocks/mockProcessInstanceListeners';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/flownodeInstances/sequenceFlows';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {
  ProcessInstance,
  SequenceFlow,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');
const mockProcessInstance: ProcessInstance = {
  processInstanceKey: '4294980768',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
};
const mockSequenceFlowsV2: SequenceFlow[] = [
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowKey: 'SequenceFlow_0drux68',
    processDefinitionId: '123',
    processDefinitionKey: 123,
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowKey: 'SequenceFlow_0j6tsnn',
    processDefinitionId: '123',
    processDefinitionKey: 123,
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowKey: 'SequenceFlow_1dwqvrt',
    processDefinitionId: '123',
    processDefinitionKey: 123,
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowKey: 'SequenceFlow_1fgekwd',
    processDefinitionId: '123',
    processDefinitionKey: 123,
  },
];

const mockRequests = (contextPath: string = '') => {
  mockFetchProcessInstanceDeprecated(contextPath).withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessInstanceDeprecated(contextPath).withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessInstanceDeprecated(contextPath).withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessInstance(contextPath).withSuccess(mockProcessInstance);
  mockFetchProcessInstance(contextPath).withSuccess(mockProcessInstance);
  mockFetchCallHierarchy(contextPath).withSuccess({items: []});
  mockFetchProcessDefinitionXml({contextPath}).withSuccess('');
  mockFetchProcessSequenceFlows().withSuccess({items: mockSequenceFlowsV2});
  mockFetchFlowNodeInstances(contextPath).withSuccess(
    processInstancesMock.level1,
  );
  mockFetchVariables(contextPath).withSuccess([createVariable()]);
  mockFetchProcessInstanceIncidents(contextPath).withSuccess({
    ...mockIncidents,
    count: 2,
  });
  mockFetchProcess(contextPath).withSuccess(mockProcess);
  mockFetchProcessInstanceListeners(contextPath).withSuccess(noListeners);
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
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
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
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return Wrapper;
}

const waitForPollingsToBeComplete = async () => {
  await waitFor(() => {
    expect(variablesStore.isPollRequestRunning).toBe(false);
    expect(processInstanceDetailsStore.isPollRequestRunning).toBe(false);
    expect(sequenceFlowsStore.isPollRequestRunning).toBe(false);
    expect(incidentsStore.isPollRequestRunning).toBe(false);
    expect(flowNodeInstanceStore.isPollRequestRunning).toBe(false);
  });
};

export {getWrapper, testData, waitForPollingsToBeComplete, mockProcessInstance};

export {mockRequests};
