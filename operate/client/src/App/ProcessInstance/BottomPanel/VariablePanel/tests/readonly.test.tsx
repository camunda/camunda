/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  createInstance,
  createvariable,
  mockProcessWithInputOutputMappingsXML,
  searchResult,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init as initFlowNodeMetadata} from 'modules/utils/flowNodeMetadata';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        flowNodeSelectionStore.reset();
        flowNodeMetaDataStore.reset();
        modificationsStore.reset();
        processInstanceDetailsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: 'instance_id',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: false,
};

describe('VariablePanel', () => {
  beforeEach(() => {
    const mockProcessInstanceDeprecated = createInstance();

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );

    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    initFlowNodeMetadata('process-instance', statistics);
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
  });

  it('should be readonly if root node is selected and no add/move modification is created yet', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchVariables().withSuccess(searchResult([createvariable()]));

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper([Paths.processInstance('processInstanceId123')]),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable testVariableName/i}),
    ).not.toBeInTheDocument();
  });

  it('should be readonly if root node is selected and only cancel modifications exist', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchVariables().withSuccess(searchResult([createvariable()]));

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    modificationsStore.enableModificationMode();
    modificationsStore.cancelToken('some-element-id', 'some-instance-key', {});

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper([Paths.processInstance('processInstanceId123')]),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable testVariableName/i}),
    ).not.toBeInTheDocument();
  });

  it.skip('should be readonly for existing nodes without add/move modifications', async () => {
    mockSearchVariables().withSuccess(searchResult([]));
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813695856',
      instanceCount: 1,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    act(() => {
      modificationsStore.enableModificationMode();
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    mockSearchVariables().withSuccess(searchResult([createvariable()]));
    mockSearchJobs().withSuccess(searchResult([]));

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
          flowNodeInstanceId: '2251799813695856',
        },
      );
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable testVariableName/i}),
    ).not.toBeInTheDocument();
  });
});
