/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {testData} from './index.setup';
import {createUser, createvariable} from 'modules/testUtils';
import {
  createMemoryRouter,
  RouterProvider,
  useNavigate,
} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcess} from 'modules/mocks/api/processes/fetchProcess';
import {mockProcess} from 'modules/mocks/api/mocks/process';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/flownodeInstances/sequenceFlows';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {type SequenceFlow} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';

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
  mockSearchVariables().withSuccess({
    items: [createvariable()],
    page: {
      totalItems: 1,
    },
  });
  mockSearchVariables().withSuccess({
    items: [],
    page: {
      totalItems: 0,
    },
  });
  mockSearchIncidentsByProcessInstance('4294980768').withSuccess({
    items: [],
    page: {totalItems: 0},
  });
  mockSearchIncidentsByProcessInstance('4294980768').withSuccess({
    items: [],
    page: {totalItems: 0},
  });
  mockFetchProcess().withSuccess(mockProcess);
  mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
  mockQueryBatchOperationItems().withSuccess({
    items: [],
    page: {totalItems: 0},
  });
  mockQueryBatchOperationItems().withSuccess({
    items: [],
    page: {totalItems: 0},
  });
  mockSearchElementInstances().withSuccess({
    items: [],
    page: {totalItems: 0},
  });
  mockSearchElementInstances().withSuccess({
    items: [],
    page: {totalItems: 0},
  });
};

type FlowNodeSelectorProps = {
  selectableFlowNode: {
    flowNodeId?: string;
    flowNodeInstanceId?: string;
    isMultiInstance?: boolean;
  };
};

const FlowNodeSelector: React.FC<FlowNodeSelectorProps> = ({
  selectableFlowNode,
}) => {
  const navigate = useNavigate();

  return (
    <button
      onClick={() => {
        const searchParams = new URLSearchParams();
        if (selectableFlowNode.flowNodeId) {
          searchParams.set('elementId', selectableFlowNode.flowNodeId);
        }
        if (selectableFlowNode.flowNodeInstanceId) {
          searchParams.set(
            'elementInstanceKey',
            selectableFlowNode.flowNodeInstanceId,
          );
        }
        if (selectableFlowNode.isMultiInstance) {
          searchParams.set('isMultiInstanceBody', 'true');
        }
        navigate(`?${searchParams.toString()}`, {replace: true});
      }}
    >
      {`Select flow node`}
    </button>
  );
};

type Props = {
  children?: React.ReactNode;
};

function getWrapper(options?: {
  initialPath?: string;
  contextPath?: string;
  selectableFlowNode?: {
    flowNodeId?: string;
    flowNodeInstanceId?: string;
    isMultiInstance?: boolean;
  };
}) {
  const {
    initialPath = Paths.processInstance('4294980768'),
    contextPath,
    selectableFlowNode,
  } = options ?? {};

  const Wrapper: React.FC<Props> = ({children}) => {
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

export {getWrapper};

export {mockRequests};
