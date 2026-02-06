/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitForElementToBeRemoved} from 'modules/testing-library';
import {mockProcessWithEventBasedGateway} from 'modules/mocks/mockProcessWithEventBasedGateway';
import {modificationsStore} from 'modules/stores/modifications';
import {renderPopover} from './mocks';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {incidentFlowNodeMetaData} from 'modules/mocks/metadata';
import {open} from 'modules/mocks/diagrams';
import {act} from 'react';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {fetchMetaData, init} from 'modules/utils/flowNodeMetadata';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';

const statisticsData = [
  {
    elementId: 'StartEvent_1',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 1,
  },
  {
    elementId: 'service-task-1',
    active: 5,
    canceled: 0,
    incidents: 0,
    completed: 1,
  },
  {
    elementId: 'multi-instance-subprocess',
    active: 0,
    canceled: 0,
    incidents: 1,
    completed: 0,
  },
  {
    elementId: 'subprocess-start-1',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 1,
  },
  {
    elementId: 'subprocess-service-task',
    active: 0,
    canceled: 0,
    incidents: 1,
    completed: 0,
  },
  {
    elementId: 'service-task-3',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 1,
  },
  {
    elementId: 'service-task-7',
    active: 1,
    canceled: 0,
    incidents: 0,
    completed: 0,
  },
  {
    elementId: 'message-boundary',
    active: 1,
    canceled: 0,
    incidents: 0,
    completed: 0,
  },
];

describe.skip('Modification Dropdown', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class ResizeObserver {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();

        constructor(callback: ResizeObserverCallback) {
          setTimeout(() => {
            try {
              callback([], this);
            } catch {
              // Ignore errors in mock
            }
          }, 0);
        }
      },
    );
    vi.stubGlobal(
      'SVGElement',
      class MockSVGElement extends Element {
        getBBox = vi.fn(() => ({
          x: 0,
          y: 0,
          width: 100,
          height: 100,
        }));
      },
    );
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

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statisticsData,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statisticsData,
    });
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
    modificationsStore.enableModificationMode();
  });

  it('should not support add modification for events attached to event based gateway', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'message_intermediate_catch_non_selectable',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'message_intermediate_catch_selectable',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          elementId: 'timer_intermediate_catch_non_selectable',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          elementId: 'message_intermediate_throw_selectable',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          elementId: 'timer_intermediate_catch_selectable',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithEventBasedGateway,
    );

    renderPopover();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'message_intermediate_catch_non_selectable',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'message_intermediate_catch_selectable',
        },
      );
    });

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'timer_intermediate_catch_non_selectable',
        },
      );
    });

    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'message_intermediate_throw_selectable',
        },
      );
    });

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'timer_intermediate_catch_selectable',
        },
      );
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
  });

  it('should not render dropdown when no flow node is selected', async () => {
    renderPopover();

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
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-1',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(
      await screen.findByTitle(/Add single flow node instance/),
    ).toHaveTextContent(/Add/);
    expect(
      await screen.findByTitle(
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

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-1',
        },
      );
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

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-3',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should only render move and cancel options for boundary events', async () => {
    renderPopover();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'message-boundary',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should render unsupported flow node type for non modifiable flow nodes', async () => {
    renderPopover();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'boundary-event',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(/Unsupported flow node type/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should not support move operation for sub processes', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    renderPopover();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'multi-instance-subprocess',
          isMultiInstance: true,
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'multi-instance-subprocess',
          isMultiInstance: false,
        },
      );
    });

    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.getByText(/Cancel/)).toBeInTheDocument();
  });

  it('should display spinner when loading meta data', async () => {
    init('process-instance', statisticsData);

    renderPopover();

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-1',
          flowNodeInstanceId: 'some-instance-key',
        },
      );
    });

    expect(screen.getByTestId('dropdown-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('dropdown-spinner'));
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(await screen.findByText(/Move instance/)).toBeInTheDocument();
    expect(screen.getByText(/Cancel instance/)).toBeInTheDocument();
  });

  it('should support cancel instance when flow node has 1 running instance', async () => {
    renderPopover();

    // select a flow node that has 1 running instance

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-7',
        },
      );
    });

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    act(() => {
      fetchMetaData(statisticsData, 'process-instance', {
        flowNodeId: 'service-task-7',
      });
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel instance/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();

    // select a flow node that has more than 1 running instances
    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-1',
        },
      );
    });

    expect(
      await screen.findByText(/Flow Node Modifications/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Cancel all/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'service-task-1',
          flowNodeInstanceId: 'some-instance-id',
        },
      );
    });

    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);

    act(() => {
      fetchMetaData(statisticsData, 'process-instance', {
        flowNodeId: 'service-task-1',
        flowNodeInstanceId: 'some-instance-id',
      });
    });

    expect(await screen.findByText(/Cancel instance/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });
});
