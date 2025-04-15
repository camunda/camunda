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
import {mockSequenceFlows, mockIncidents} from './index.setup';
import {TopPanel} from './index';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  calledInstanceMetadata,
  incidentFlowNodeMetaData,
  PROCESS_INSTANCE_ID,
} from 'modules/mocks/metadata';
import {createInstance} from 'modules/testUtils';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchSequenceFlows} from 'modules/mocks/api/processInstances/sequenceFlows';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {open} from 'modules/mocks/diagrams';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';
import {Paths} from 'modules/Routes';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

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

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
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
    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    mockFetchProcessInstance().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'}),
    );
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);
    mockFetchSequenceFlows().withSuccess(mockSequenceFlows);
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'service-task-1',
        active: 0,
        incidents: 1,
        completed: 0,
        canceled: 0,
      },
      {
        activityId: 'service-task-7',
        active: 5,
        incidents: 1,
        completed: 0,
        canceled: 0,
      },
    ]);
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStatisticsStore.init('id');
  });

  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
    processInstanceDetailsStatisticsStore.reset();
  });

  it('should render spinner while loading', async () => {
    mockFetchProcessInstance().withSuccess(createInstance({id: 'instance_id'}));

    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'active_instance'});

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    expect(await screen.findByText('1 Incident occurred')).toBeInTheDocument();
  });

  it('should show an error when a server error occurs', async () => {
    mockFetchProcessXML().withServerError();
    mockFetchProcessDefinitionXml().withServerError();

    render(<TopPanel />, {
      wrapper: Wrapper,
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

    mockFetchProcessXML().withNetworkError();
    mockFetchProcessDefinitionXml().withNetworkError();

    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should toggle incident bar', async () => {
    const {user} = render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});

    expect(
      screen.queryByText('Incidents View - 1 result'),
    ).not.toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.getByText('Incidents View - 1 result')).toBeInTheDocument();

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
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(processInstanceDetailsStatisticsStore.state.status).toBe(
        'fetched',
      ),
    );

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

  it('should display move token banner in moving mode', async () => {
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    const {user} = render(<TopPanel />, {
      wrapper: Wrapper,
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

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      await screen.findByText(/select the target flow node in the diagram/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Discard'}));

    expect(
      screen.queryByText(/select the target flow node in the diagram/i),
    ).not.toBeInTheDocument();
  });

  it('should display multiple instances banner when a flow node with multiple running instances is selected', async () => {
    processInstanceDetailsStore.init({id: 'active_instance'});

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    render(<TopPanel />, {
      wrapper: Wrapper,
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

  (IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED ? it : it.skip)(
    'should display parent selection banner when trying to add a token on a flow node that has multiple scopes',
    async () => {
      mockFetchProcessXML().withSuccess(mockNestedSubprocess);

      processInstanceDetailsStore.init({id: 'active_instance'});
      processInstanceDetailsDiagramStore.init();

      mockFetchProcessInstanceDetailStatistics().withSuccess([
        {
          activityId: 'parent_sub_process',
          active: 2,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          activityId: 'inner_sub_process',
          active: 2,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          activityId: 'user_task',
          active: 2,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ]);
      mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

      const {user} = render(<TopPanel />, {
        wrapper: Wrapper,
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
