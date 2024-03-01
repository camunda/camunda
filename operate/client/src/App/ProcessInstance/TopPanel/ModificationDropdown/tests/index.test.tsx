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
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {mockProcessWithEventBasedGateway} from 'modules/mocks/mockProcessWithEventBasedGateway';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {renderPopover} from './mocks';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {incidentFlowNodeMetaData} from 'modules/mocks/metadata';
import {open} from 'modules/mocks/diagrams';
import {act} from 'react-dom/test-utils';

describe('Modification Dropdown', () => {
  beforeEach(() => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'StartEvent_1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'service-task-1',
        active: 5,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'multi-instance-subprocess',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'subprocess-start-1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'subprocess-service-task',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'service-task-3',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'service-task-7',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'message-boundary',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));
    modificationsStore.enableModificationMode();
  });

  it('should not render dropdown when no flow node is selected', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    expect(
      screen.queryByText(/Flow Node Modifications/),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/Add single flow node instance/),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Cancel all running flow node instances in this flow node/,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Move all running instances in this flow node to another target/,
      ),
    ).not.toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-1',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(/Add single flow node instance/),
    ).toHaveTextContent(/Add/);
    expect(
      screen.getByTitle(
        /Cancel all running flow node instances in this flow node/,
      ),
    ).toHaveTextContent(/Cancel/);
    expect(
      screen.getByTitle(
        /Move all running instances in this flow node to another target/,
      ),
    ).toHaveTextContent(/Move/);
  });

  it('should not render dropdown when moving token', async () => {
    const {user} = renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-1',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();

    await user.click(await screen.findByText(/Move all/));

    expect(
      screen.queryByText(/Flow Node Modifications/),
    ).not.toBeInTheDocument();
  });

  it('should only render add option for completed flow nodes', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-3',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should only render move and cancel options for boundary events', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'message-boundary',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should render unsupported flow node type for non modifiable flow nodes', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'boundary-event',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Unsupported flow node type/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should not support add modification for events attached to event based gateway', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'message_intermediate_catch_non_selectable',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'message_intermediate_catch_selectable',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'timer_intermediate_catch_non_selectable',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'message_intermediate_throw_selectable',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'timer_intermediate_catch_selectable',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(mockProcessWithEventBasedGateway);

    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'message_intermediate_catch_non_selectable',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'message_intermediate_catch_selectable',
      });
    });

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'timer_intermediate_catch_non_selectable',
      });
    });

    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'message_intermediate_throw_selectable',
      });
    });

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'timer_intermediate_catch_selectable',
      });
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
  });

  it('should not support move operation for sub processes', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'multi-instance-subprocess',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'multi-instance-subprocess',
        isMultiInstance: true,
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'multi-instance-subprocess',
        isMultiInstance: false,
      });
    });

    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
  });

  it('should display spinner when loading meta data', async () => {
    flowNodeMetaDataStore.init();

    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-1',
        flowNodeInstanceId: 'some-instance-key',
      });
    });

    expect(await screen.findByTestId('dropdown-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(() =>
      screen.getByTestId('dropdown-spinner'),
    );
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.getByText(/Move instance/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel instance/)).toBeInTheDocument();
  });

  it('should support cancel instance when flow node has 1 running instance', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull(),
    );

    // select a flow node that has 1 running instance

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-7',
      });
    });

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    act(() => {
      flowNodeMetaDataStore.fetchMetaData({flowNodeId: 'service-task-7'});
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel instance/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();

    // select a flow node that has more than 1 running instances
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-1',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel all/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();

    // select a flow node instance
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'service-task-1',
        flowNodeInstanceId: 'some-instance-id',
      });
    });

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    act(() => {
      flowNodeMetaDataStore.fetchMetaData({
        flowNodeId: 'service-task-1',
        flowNodeInstanceId: 'some-instance-id',
      });
    });

    expect(await screen.findByText(/Cancel instance/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });
});
