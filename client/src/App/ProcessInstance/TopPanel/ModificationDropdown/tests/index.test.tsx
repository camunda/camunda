/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

describe('Modification Dropdown', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

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
  });

  afterEach(() => {
    flowNodeMetaDataStore.reset();
  });

  it('should not render dropdown when no flow node is selected', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );

    modificationsStore.enableModificationMode();

    expect(
      screen.queryByText(/Flow Node Modifications/)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/Add single flow node instance/)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Cancel all running flow node instances in this flow node/
      )
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Move all running instances in this flow node to another target/
      )
    ).not.toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(/Add single flow node instance/)
    ).toHaveTextContent(/Add/);
    expect(
      screen.getByTitle(
        /Cancel all running flow node instances in this flow node/
      )
    ).toHaveTextContent(/Cancel/);
    expect(
      screen.getByTitle(
        /Move all running instances in this flow node to another target/
      )
    ).toHaveTextContent(/Move/);
  });

  it('should not render dropdown when moving token', async () => {
    const {user} = renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });
    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();

    await user.click(await screen.findByText(/Move all/));

    expect(
      screen.queryByText(/Flow Node Modifications/)
    ).not.toBeInTheDocument();
  });

  it('should only render add option for completed flow nodes', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-3',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should only render move and cancel options for boundary events', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'message-boundary',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should render unsupported flow node type for non modifiable flow nodes', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'boundary-event',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
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
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'message_intermediate_catch_non_selectable',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'message_intermediate_catch_selectable',
    });

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'timer_intermediate_catch_non_selectable',
    });

    await waitForElementToBeRemoved(() => screen.getByText(/Add/));
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'message_intermediate_throw_selectable',
    });

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'timer_intermediate_catch_selectable',
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
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'multi-instance-subprocess',
      isMultiInstance: true,
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'multi-instance-subprocess',
      isMultiInstance: false,
    });

    await waitForElementToBeRemoved(() => expect(screen.getByText(/Add/)));
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
  });

  it('should display spinner when loading meta data', async () => {
    flowNodeMetaDataStore.init();

    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: 'some-instance-key',
    });

    expect(await screen.findByTestId('dropdown-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(() =>
      screen.getByTestId('dropdown-spinner')
    );
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.getByText(/Move instance/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel instance/)).toBeInTheDocument();
  });

  it('should support cancel instance when flow node has 1 running instance', async () => {
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    // select a flow node that has 1 running instance
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-7',
    });

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    flowNodeMetaDataStore.fetchMetaData({flowNodeId: 'service-task-7'});

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel instance/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();

    // select a flow node that has more than 1 running instances
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
    });

    expect(
      await screen.findByText(/Flow Node Modifications/)
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel all/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();

    // select a flow node instance
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: 'some-instance-id',
    });

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    flowNodeMetaDataStore.fetchMetaData({
      flowNodeId: 'service-task-1',
      flowNodeInstanceId: 'some-instance-id',
    });
    expect(await screen.findByText(/Cancel instance/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });
});
