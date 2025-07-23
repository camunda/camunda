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
import {testData} from './index.setup';
import {
  createMultiInstanceFlowNodeInstances,
  createUser,
  createVariable,
  createVariableV2,
} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {
  type Selection,
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
import {mockProcess} from 'modules/mocks/api/mocks/process';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';
import {noListeners} from 'modules/mocks/mockProcessInstanceListeners';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/flownodeInstances/sequenceFlows';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {type SequenceFlow} from '@vzeta/camunda-api-zod-schemas/8.8';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');

const mockSequenceFlowsV2: SequenceFlow[] = [
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_0drux68',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_0j6tsnn',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_1dwqvrt',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_1fgekwd',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
];

const mockRequests = () => {
  mockMe().withSuccess(createUser());
  mockFetchProcessInstanceDeprecated().withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessInstanceDeprecated().withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessInstanceDeprecated().withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessInstance().withSuccess(mockProcessInstance);
  mockFetchProcessInstance().withSuccess(mockProcessInstance);
  mockFetchCallHierarchy().withSuccess([]);
  mockFetchProcessDefinitionXml().withSuccess('');
  mockFetchProcessSequenceFlows().withSuccess({items: mockSequenceFlowsV2});
  mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
  mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
  mockFetchFlownodeInstancesStatistics().withSuccess({
    items: [
      {
        elementId: 'service-task-1',
        active: 0,
        incidents: 1,
        completed: 0,
        canceled: 0,
      },
      {
        elementId: 'service-task-7',
        active: 5,
        incidents: 1,
        completed: 0,
        canceled: 0,
      },
    ],
  });
  mockFetchVariables().withSuccess([createVariable()]);
  mockSearchVariables().withSuccess({
    items: [createVariableV2()],
    page: {
      totalItems: 1,
    },
  });
  mockFetchVariables().withSuccess([createVariable()]);
  mockFetchProcessInstanceIncidents().withSuccess({
    ...mockIncidents,
    count: 2,
  });
  mockFetchProcessInstanceIncidents().withSuccess({
    ...mockIncidents,
    count: 2,
  });
  mockFetchProcess().withSuccess(mockProcess);
  mockFetchProcessInstanceListeners().withSuccess(noListeners);
};

type FlowNodeSelectorProps = {
  selectableFlowNode: Selection;
};

const FlowNodeSelector: React.FC<FlowNodeSelectorProps> = ({
  selectableFlowNode,
}) => (
  <button
    onClick={() => {
      selectFlowNode({}, selectableFlowNode);
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

    const router = createMemoryRouter(
      [
        {
          path: Paths.processInstance(),
          element: (
            <>
              {children}
              {selectableFlowNode && (
                <FlowNodeSelector selectableFlowNode={selectableFlowNode} />
              )}
              <LocationLog />
            </>
          ),
        },
        {
          path: Paths.processes(),
          element: (
            <>
              instances page
              <LocationLog />
            </>
          ),
        },
        {
          path: Paths.dashboard(),
          element: <>dashboard page</>,
        },
      ],
      {
        initialEntries: [initialPath],
        basename: contextPath ?? '',
      },
    );

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return Wrapper;
}

const waitForPollingsToBeComplete = async () => {
  await waitFor(() => {
    expect(variablesStore.isPollRequestRunning).toBe(false);
    // eslint-disable-next-line testing-library/no-wait-for-multiple-assertions
    expect(processInstanceDetailsStore.isPollRequestRunning).toBe(false);
    // eslint-disable-next-line testing-library/no-wait-for-multiple-assertions
    expect(sequenceFlowsStore.isPollRequestRunning).toBe(false);
    // eslint-disable-next-line testing-library/no-wait-for-multiple-assertions
    expect(incidentsStore.isPollRequestRunning).toBe(false);
    // eslint-disable-next-line testing-library/no-wait-for-multiple-assertions
    expect(flowNodeInstanceStore.isPollRequestRunning).toBe(false);
  });
};

export {getWrapper, waitForPollingsToBeComplete, processInstancesMock};

export {mockRequests};
