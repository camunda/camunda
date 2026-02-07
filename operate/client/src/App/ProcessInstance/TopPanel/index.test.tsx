/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {TopPanel} from './index';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  calledInstanceMetadata,
  incidentFlowNodeMetaData,
  PROCESS_INSTANCE_ID,
} from 'modules/mocks/metadata';
import {createInstance, createIncident} from 'modules/testUtils';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {open} from 'modules/mocks/diagrams';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/flownodeInstances/sequenceFlows';
import type {
  ElementInstance,
  ProcessInstance,
  SequenceFlow,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchMessageSubscriptions} from 'modules/mocks/api/v2/messageSubscriptions/searchMessageSubscriptions';
import * as modificationsUtils from 'modules/utils/modifications';

const mockIncidents = {
  page: {totalItems: 1},
  items: [
    createIncident({errorType: 'CONDITION_ERROR', elementId: 'Service5678'}),
  ],
};

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

vi.mock('react-transition-group', () => {
  const FakeTransition = vi.fn(({children}) => children);
  const FakeCSSTransition = vi.fn((props) =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null,
  );

  return {
    CSSTransition: FakeCSSTransition,
    Transition: FakeTransition,
    TransitionGroup: vi.fn(({children}) => {
      return children.map((transition: {props: object}) => {
        const completedTransition = {...transition};
        completedTransition.props = {...transition.props, in: true};
        return completedTransition;
      });
    }),
  };
});

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: 'instance_id',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
};

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: 'service-task-1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionId: 'process-def-1',
  processInstanceKey: 'instance_id',
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

describe('TopPanel', () => {
  beforeAll(() => {
    //@ts-expect-error - Use to mute act warnings
    // eslint-disable-next-line no-undef
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-expect-error - Use to mute act warnings
    // eslint-disable-next-line no-undef
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'}),
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchIncidentsByProcessInstance(':instance_id').withSuccess(
      mockIncidents,
    );
    mockSearchIncidentsByProcessInstance(':instance_id').withSuccess(
      mockIncidents,
    );
    mockSearchIncidentsByProcessInstance(':instance_id').withSuccess(
      mockIncidents,
    );
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

    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchMessageSubscriptions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockServer.use(
      http.post('/api/process-instances/:instanceId/flow-node-metadata', () => {
        return HttpResponse.json(calledInstanceMetadata);
      }),
    );
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
    modificationsStore.reset();
    flowNodeMetaDataStore.reset();
  });

  it('should render spinner while loading', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      hasIncident: false,
    });

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'active_instance'});

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    expect(await screen.findByText('1 Incident occurred')).toBeInTheDocument();
  });

  it('should show an error when a server error occurs', async () => {
    mockFetchProcessDefinitionXml().withServerError();
    mockFetchProcessDefinitionXml().withServerError();

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should show an error when a network error occurs', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchProcessDefinitionXml().withNetworkError();

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should show permissions error when access to the process definition is forbidden', async () => {
    mockFetchProcessDefinitionXml().withServerError(403);
    mockFetchProcessDefinitionXml().withServerError(403);

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});

    expect(
      await screen.findByText('Missing permissions to view the Definition'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Please contact your organization owner or admin to give you the necessary permissions to read this definition',
      ),
    ).toBeInTheDocument();
  });

  it('should toggle incident bar', async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'}),
    );

    const {user} = render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});

    await waitFor(() =>
      expect(
        screen.queryByText('Incidents - 1 result'),
      ).not.toBeInTheDocument(),
    );

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(await screen.findByText('Incidents - 1 result')).toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.queryByText('Incidents - 1 result')).not.toBeInTheDocument();
  });

  it('should render metadata for default mode and modification dropdown for modification mode', async () => {
    mockFetchFlowNodeMetadata().withSuccess({
      ...calledInstanceMetadata,
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: '2251799813699889',
    });
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockElementInstance,
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchElementInstances().withSuccess({
      items: [mockElementInstance],
      page: {totalItems: 1},
    });

    mockSearchIncidentsByProcessInstance('instance_id').withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: mockElementInstance.elementId,
          active: 1,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    });

    flowNodeMetaDataStore.setMetaData({
      ...calledInstanceMetadata,
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: '2251799813699889',
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-1',
        flowNodeInstanceId: '2251799813699889',
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('diagram-spinner'),
    );

    await waitFor(() =>
      expect(
        screen.queryByText(/Element Instance Key/),
      ).not.toBeInTheDocument(),
    );

    await screen.findByText(/Execution Duration/);

    modificationsStore.enableModificationMode();

    expect(screen.queryByText(/Start Date/)).not.toBeInTheDocument();
    expect(screen.queryByText(/End Date/)).not.toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-1',
      },
    );

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(
      await screen.findByTitle(/Add single flow node instance/),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(/Cancel selected instance in this flow node/),
    ).toBeInTheDocument();

    expect(
      screen.getByTitle(
        /Move selected instance in this flow node to another target/,
      ),
    ).toBeInTheDocument();
  });

  it('should display multiple instances banner when a flow node with multiple running instances is selected', async () => {
    processInstanceDetailsStore.init({id: 'active_instance'});

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    modificationsStore.enableModificationMode();

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-7',
      },
    );

    expect(
      await screen.findByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-1',
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-7',
      },
    );

    expect(
      await screen.findByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-7',
        flowNodeInstanceId: 'some-instance-id',
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    );
  });

  it('should display move token banner in moving mode', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    const {user} = render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    modificationsStore.enableModificationMode();

    selectFlowNode(
      {},
      {
        flowNodeId: 'service-task-1',
      },
    );

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/select the target flow node in the diagram/i),
    ).not.toBeInTheDocument();

    await user.click(await screen.findByRole('button', {name: /move/i}));

    expect(
      await screen.findByText(/select the target flow node in the diagram/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Discard'}));

    expect(
      screen.queryByText(/select the target flow node in the diagram/i),
    ).not.toBeInTheDocument();
  });

  it('should pass the ancestor type if move modification requires an ancestor', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('subprocessInsideMultiInstance.bpmn'),
    );

    const parentElement = {
      flowNodeId: 'sub-2',
    };
    const element = {
      flowNodeInstanceId: '2251799813699889',
      flowNodeId: 'task-1',
    };

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: element.flowNodeId,
          active: 2,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
        {
          elementId: parentElement.flowNodeId,
          active: 2,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    });

    flowNodeMetaDataStore.setMetaData({
      ...calledInstanceMetadata,
      ...element,
      instanceMetadata: {
        ...calledInstanceMetadata.instanceMetadata,
        endDate: null,
      },
    });

    const finishMovingTokenSpy = vi.spyOn(
      modificationsUtils,
      'finishMovingToken',
    );

    const {user} = render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    modificationsStore.enableModificationMode();

    selectFlowNode({}, element);

    expect(
      screen.queryByText(/select the target flow node in the diagram/i),
    ).not.toBeInTheDocument();

    await user.click(await screen.findByRole('button', {name: /move/i}));

    expect(
      await screen.findByText(/select the target flow node in the diagram/i),
    ).toBeInTheDocument();

    await user.click(await screen.findByTestId('task-2'));

    expect(finishMovingTokenSpy).toHaveBeenCalledWith(
      expect.any(Number),
      expect.any(Number),
      expect.any(Object),
      expect.any(String),
      'task-2',
      'sourceParent',
    );

    finishMovingTokenSpy.mockRestore();
  });

  /* eslint-disable vitest/no-standalone-expect -- eslint doesn't understand dynamically skipped tests */
  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it : it.skip)(
    'should display parent selection banner when trying to add a token on a flow node that has multiple scopes',
    async () => {
      mockFetchProcessDefinitionXml().withSuccess(mockNestedSubprocess);

      processInstanceDetailsStore.init({id: 'active_instance'});

      mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

      const {user} = render(<TopPanel />, {
        wrapper: getWrapper(),
      });

      modificationsStore.enableModificationMode();

      selectFlowNode(
        {},
        {
          flowNodeId: 'user_task',
        },
      );

      await user.click(
        await screen.findByTitle(/Add single flow node instance/),
      );

      expect(
        await screen.findByText(
          /Flow node has multiple parent scopes. Please select parent node from Instance History to Add./i,
        ),
      ).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: 'Discard'}));

      expect(
        screen.queryByText(
          /Flow node has multiple parent scopes. Please select parent node from Instance History to Add./i,
        ),
      ).not.toBeInTheDocument();
    },
  );
  /* eslint-enable vitest/no-standalone-expect */
});
