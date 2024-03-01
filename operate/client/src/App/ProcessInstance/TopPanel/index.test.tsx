/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
      </Routes>
    </MemoryRouter>
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
