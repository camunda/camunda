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
import {mockSequenceFlowsV2, mockIncidents} from './index.setup';
import {TopPanel} from './index';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  calledInstanceMetadata,
  incidentFlowNodeMetaData,
  PROCESS_INSTANCE_ID,
} from 'modules/mocks/metadata';
import {createInstance} from 'modules/testUtils';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
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
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

jest.mock('react-transition-group', () => {
  const FakeTransition = jest.fn(({children}) => children);
  const FakeCSSTransition = jest.fn((props) =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null,
  );

  return {
    CSSTransition: FakeCSSTransition,
    Transition: FakeTransition,
    TransitionGroup: jest.fn(({children}) => {
      return children.map((transition: any) => {
        const completedTransition = {...transition};
        completedTransition.props = {...transition.props, in: true};
        return completedTransition;
      });
    }),
  };
});

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
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
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

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'}),
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);
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
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
    modificationsStore.reset();
    jest.clearAllMocks();
  });

  it('should render spinner while loading', async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({id: 'instance_id'}),
    );

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'active_instance'});

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchProcessDefinitionXml().withNetworkError();

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should toggle incident bar', async () => {
    const {user} = render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});

    await waitFor(() =>
      expect(
        screen.queryByText('Incidents View - 1 result'),
      ).not.toBeInTheDocument(),
    );

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(
      await screen.findByText('Incidents View - 1 result'),
    ).toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(
      screen.queryByText('Incidents View - 1 result'),
    ).not.toBeInTheDocument();
  });

  it('should render metadata for default mode and modification dropdown for modification mode', async () => {
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(
      await screen.findByText(/Flow Node Instance Key/),
    ).toBeInTheDocument();

    expect(screen.getByText(/Execution Duration/)).toBeInTheDocument();

    modificationsStore.enableModificationMode();

    await waitFor(() =>
      expect(
        screen.queryByText(/Flow Node Instance Key/),
      ).not.toBeInTheDocument(),
    );

    expect(screen.queryByText(/Start Date/)).not.toBeInTheDocument();
    expect(screen.queryByText(/End Date/)).not.toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

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

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
    });

    expect(
      await screen.findByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    );

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
    });

    expect(
      await screen.findByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i,
      ),
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
      flowNodeInstanceId: 'some-instance-id',
    });

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

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

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

      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'user_task',
      });

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
});
