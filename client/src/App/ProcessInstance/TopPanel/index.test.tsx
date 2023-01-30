/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
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

jest.mock('react-transition-group', () => {
  const FakeTransition = jest.fn(({children}) => children);
  const FakeCSSTransition = jest.fn((props) =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null
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
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
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

    mockFetchProcessInstance().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'})
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

    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(await screen.findByText('1 Incident occurred')).toBeInTheDocument();
  });

  it('should show an error when a server error occurs', async () => {
    mockFetchProcessXML().withServerError();

    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });

  it('should show an error when a network error occurs', async () => {
    mockFetchProcessXML().withNetworkError();

    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });

  it('should toggle incident bar', async () => {
    const {user} = render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(screen.queryByText('Incident Type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.getByText('Incident type:')).toBeInTheDocument();
    expect(screen.getByText('Flow Node:')).toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.queryByText('Incident type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();
  });

  it('should render metadata for default mode and modification dropdown for modification mode', async () => {
    processInstanceDetailsDiagramStore.init();
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(processInstanceDetailsStatisticsStore.state.status).toBe('fetched')
    );

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(
      await screen.findByText(/Flow Node Instance Key/)
    ).toBeInTheDocument();

    expect(screen.getByText(/Execution Duration/)).toBeInTheDocument();

    modificationsStore.enableModificationMode();

    await waitFor(() =>
      expect(
        screen.queryByText(/Flow Node Instance Key/)
      ).not.toBeInTheDocument()
    );

    expect(screen.queryByText(/Start Date/)).not.toBeInTheDocument();
    expect(screen.queryByText(/End Date/)).not.toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(/Add single flow node instance/)
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(/Cancel selected instance in this flow node/)
    ).toBeInTheDocument();

    expect(
      screen.getByTitle(
        /Move all running instances in this flow node to another target/
      )
    ).toBeInTheDocument();
  });

  it('should display move token banner in moving mode', async () => {
    processInstanceDetailsDiagramStore.init();

    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      })
    );

    const {user} = render(<TopPanel />, {
      wrapper: Wrapper,
    });

    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/select the target flow node in the diagram/i)
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /move/i}));

    expect(
      await screen.findByText(/select the target flow node in the diagram/i)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Discard'}));

    expect(
      screen.queryByText(/select the target flow node in the diagram/i)
    ).not.toBeInTheDocument();
  });

  it('should display multiple instances banner when a flow node with multiple running instances is selected', async () => {
    processInstanceDetailsStore.init({id: 'active_instance'});
    processInstanceDetailsDiagramStore.init();

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
        /Flow node has multiple instances. To select one, use the instance history tree below./i
      )
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i
      )
    );

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
    });

    expect(
      await screen.findByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i
      )
    ).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
      flowNodeInstanceId: 'some-instance-id',
    });

    expect(
      await screen.findByText(
        /Flow node has multiple instances. To select one, use the instance history tree below./i
      )
    ).toBeInTheDocument();
  });
});
