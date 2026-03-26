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
import {open} from 'modules/mocks/diagrams';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {Paths} from 'modules/Routes';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';

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

describe('Modification Dropdown', () => {
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
      endDate: null,
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionVersionTag: null,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    };

    mockFetchElementInstancesStatistics().withSuccess({
      items: statisticsData,
    });
    mockFetchElementInstancesStatistics().withSuccess({
      items: statisticsData,
    });
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );
    mockSearchElementInstances().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    modificationsStore.enableModificationMode();
  });

  it('should not support add modification for non-selectable events attached to event based gateway', async () => {
    mockFetchElementInstancesStatistics().withSuccess({
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

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=message_intermediate_catch_non_selectable`,
    ]);

    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should support add modification for selectable events attached to event based gateway', async () => {
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'message_intermediate_catch_selectable',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithEventBasedGateway,
    );

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=message_intermediate_catch_selectable`,
    ]);

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
  });

  it('should not support add modification for non-selectable timer events attached to event based gateway', async () => {
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'timer_intermediate_catch_non_selectable',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithEventBasedGateway,
    );

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=timer_intermediate_catch_non_selectable`,
    ]);

    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
  });

  it('should support add modification for selectable message throw events', async () => {
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'message_intermediate_throw_selectable',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithEventBasedGateway,
    );

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=message_intermediate_throw_selectable`,
    ]);

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
  });

  it('should support add modification for selectable timer events', async () => {
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
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

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=timer_intermediate_catch_selectable`,
    ]);

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
  });

  it('should not render dropdown when no element is selected', async () => {
    renderPopover();

    expect(screen.queryByText(/Element Modifications/)).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(/Add single element instance/),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Cancel all running element instances in this element/,
      ),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTitle(
        /Move all running instances in this element to another target/,
      ),
    ).not.toBeInTheDocument();
  });

  it('should render dropdown with all options when a element is selected', async () => {
    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1`,
    ]);

    expect(
      await screen.findByTitle(/Add single element instance/),
    ).toHaveTextContent(/Add/);
    expect(
      await screen.findByTitle(
        /Cancel all running element instances in this element/,
      ),
    ).toHaveTextContent(/Cancel/);
    expect(
      screen.getByTitle(
        /Move all running instances in this element to another target/,
      ),
    ).toHaveTextContent(/Move/);
  });

  it('should not render dropdown when moving token', async () => {
    const {user} = renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1`,
    ]);

    await user.click(await screen.findByText(/Move all/));

    expect(screen.queryByText(/Element Modifications/)).not.toBeInTheDocument();
  });

  it('should only render add option for completed elements', async () => {
    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-3`,
    ]);

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should only render move and cancel options for boundary events', async () => {
    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=message-boundary`,
    ]);

    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should render unsupported element type for non modifiable elements', async () => {
    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=boundary-event`,
    ]);

    expect(
      await screen.findByText(/Unsupported element type/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should not support move operation for sub processes', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=multi-instance-subprocess&isMultiInstanceBody=true`,
    ]);

    expect(await screen.findByText(/Add/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should not support move operation for non-multi-instance sub processes', async () => {
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'multi-instance-subprocess',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
      ],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=multi-instance-subprocess`,
    ]);

    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
  });

  it('should display spinner when loading element instance data', async () => {
    mockFetchElementInstance('some-instance-key').withSuccess({
      elementInstanceKey: 'some-instance-key',
      elementId: 'service-task-1',
      elementName: 'Service Task 1',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionId: 'someKey',
      processInstanceKey: 'instance_id',
      processDefinitionKey: '2',
      rootProcessInstanceKey: null,
      hasIncident: false,
      incidentKey: null,
      tenantId: '<default>',
    });

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1&elementInstanceKey=some-instance-key`,
    ]);

    expect(screen.getByTestId('dropdown-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('dropdown-spinner'));
  });

  it('should support cancel instance when element has 1 running instance', async () => {
    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-7`,
    ]);

    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
  });

  it('should support cancel all when element has multiple running instances', async () => {
    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1`,
    ]);

    expect(await screen.findByText(/Cancel all/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
  });

  it('should not show modifications for a terminated element instance', async () => {
    mockFetchElementInstance('terminated-instance-key').withSuccess({
      elementInstanceKey: 'terminated-instance-key',
      elementId: 'service-task-1',
      elementName: 'Service Task 1',
      type: 'SERVICE_TASK',
      state: 'TERMINATED',
      startDate: '2018-06-21',
      endDate: '2018-06-22',
      processDefinitionId: 'someKey',
      processInstanceKey: 'instance_id',
      processDefinitionKey: '2',
      rootProcessInstanceKey: null,
      hasIncident: false,
      incidentKey: null,
      tenantId: '<default>',
    });

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1&elementInstanceKey=terminated-instance-key`,
    ]);

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('dropdown-spinner'),
    );

    expect(screen.getByText(/No modifications available/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should not show modifications for a completed element instance', async () => {
    mockFetchElementInstance('completed-instance-key').withSuccess({
      elementInstanceKey: 'completed-instance-key',
      elementId: 'service-task-1',
      elementName: 'Service Task 1',
      type: 'SERVICE_TASK',
      state: 'COMPLETED',
      startDate: '2018-06-21',
      endDate: '2018-06-22',
      processDefinitionId: 'someKey',
      processInstanceKey: 'instance_id',
      processDefinitionKey: '2',
      rootProcessInstanceKey: null,
      hasIncident: false,
      incidentKey: null,
      tenantId: '<default>',
    });

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1&elementInstanceKey=completed-instance-key`,
    ]);

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('dropdown-spinner'),
    );

    expect(screen.getByText(/No modifications available/)).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should display message when there are multiple instances and hide when specific instance is selected', async () => {
    const {unmount} = renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1`,
    ]);

    expect(
      await screen.findByText(/To modify a specific instance/i),
    ).toBeInTheDocument();

    unmount();

    mockFetchElementInstance('some-instance-key').withSuccess({
      elementInstanceKey: 'some-instance-key',
      elementId: 'service-task-1',
      elementName: 'Service Task 1',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionId: 'someKey',
      processInstanceKey: 'instance_id',
      processDefinitionKey: '2',
      rootProcessInstanceKey: null,
      hasIncident: false,
      incidentKey: null,
      tenantId: '<default>',
    });

    mockFetchProcessInstance().withSuccess({
      processInstanceKey: 'instance_id',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionVersionTag: null,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    });

    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    mockFetchElementInstancesStatistics().withSuccess({
      items: statisticsData,
    });

    renderPopover([
      `${Paths.processInstance('instance_id')}?elementId=service-task-1&elementInstanceKey=some-instance-key`,
    ]);

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('dropdown-spinner'),
    );

    expect(
      screen.queryByText(/To modify a specific instance/i),
    ).not.toBeInTheDocument();
  });
});
