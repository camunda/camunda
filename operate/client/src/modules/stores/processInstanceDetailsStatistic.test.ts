/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {modificationsStore} from './modifications';
import {mockComplexProcess} from 'modules/mocks/mockComplexProcess';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {mockSubProcesses} from 'modules/mocks/mockSubProcesses';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance} from 'modules/testUtils';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

const PROCESS_INSTANCE_ID = '2251799813686320';

const mockProcessInstanceDetailsStatistics = [
  {
    activityId: 'inclGatewayFork',
    active: 2,
    canceled: 0,
    incidents: 0,
    completed: 1,
  },
  {
    activityId: 'exclusiveGateway',
    active: 0,
    canceled: 2,
    incidents: 1,
    completed: 0,
  },
  {
    activityId: 'startEvent',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 25,
  },
  {
    activityId: 'alwaysFailingTask',
    active: 0,
    canceled: 0,
    incidents: 4,
    completed: 0,
  },
  {
    activityId: 'messageCatchEvent',
    active: 5,
    canceled: 0,
    incidents: 1,
    completed: 0,
  },
  {
    activityId: 'endEvent',
    active: 0,
    canceled: 0,
    incidents: 0,
    completed: 12,
  },
];

const mockAndFetchDiagram = () => {
  mockFetchProcessXML().withSuccess(mockComplexProcess);
  processInstanceDetailsDiagramStore.fetchProcessXml(PROCESS_INSTANCE_ID);
};

describe('stores/processInstanceDetailsStatistics', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess(
      mockProcessInstanceDetailsStatistics,
      {expectPolling: false},
    );
    mockFetchProcessInstance().withSuccess(
      createInstance({id: PROCESS_INSTANCE_ID, state: 'INCIDENT'}),
    );
    processInstanceDetailsStore.fetchProcessInstance(PROCESS_INSTANCE_ID);
  });

  afterEach(() => {
    processInstanceDetailsStatisticsStore.reset();
    modificationsStore.reset();
  });

  it('should get statistics', async () => {
    mockAndFetchDiagram();
    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        mockProcessInstanceDetailsStatistics,
      );
    });

    expect(
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        'inclGatewayFork',
      ),
    ).toBe(2);
    expect(
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        'exclusiveGateway',
      ),
    ).toBe(1);
    expect(
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        'startEvent',
      ),
    ).toBe(0);
    expect(
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        'alwaysFailingTask',
      ),
    ).toBe(4);
    expect(
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        'messageCatchEvent',
      ),
    ).toBe(6);
    expect(
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        'endEvent',
      ),
    ).toBe(0);
    expect(processInstanceDetailsStatisticsStore.flowNodeStatistics).toEqual([
      {count: 2, flowNodeId: 'inclGatewayFork', flowNodeState: 'active'},
      {count: 1, flowNodeId: 'inclGatewayFork', flowNodeState: 'completed'},
      {count: 1, flowNodeId: 'exclusiveGateway', flowNodeState: 'incidents'},
      {count: 2, flowNodeId: 'exclusiveGateway', flowNodeState: 'canceled'},
      {count: 25, flowNodeId: 'startEvent', flowNodeState: 'completed'},
      {count: 4, flowNodeId: 'alwaysFailingTask', flowNodeState: 'incidents'},
      {count: 5, flowNodeId: 'messageCatchEvent', flowNodeState: 'active'},
      {count: 1, flowNodeId: 'messageCatchEvent', flowNodeState: 'incidents'},
      {count: 12, flowNodeId: 'endEvent', flowNodeState: 'completedEndEvents'},
    ]);

    modificationsStore.enableModificationMode();

    expect(processInstanceDetailsStatisticsStore.flowNodeStatistics).toEqual([
      {count: 2, flowNodeId: 'inclGatewayFork', flowNodeState: 'active'},
      {count: 1, flowNodeId: 'exclusiveGateway', flowNodeState: 'incidents'},
      {count: 4, flowNodeId: 'alwaysFailingTask', flowNodeState: 'incidents'},
      {count: 5, flowNodeId: 'messageCatchEvent', flowNodeState: 'active'},
      {count: 1, flowNodeId: 'messageCatchEvent', flowNodeState: 'incidents'},
    ]);
  });

  it('should get executed/completed flow nodes', async () => {
    mockAndFetchDiagram();
    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        mockProcessInstanceDetailsStatistics,
      );
    });

    expect(processInstanceDetailsStatisticsStore.executedFlowNodes).toEqual([
      {
        activityId: 'inclGatewayFork',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'startEvent',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 25,
      },
      {
        activityId: 'endEvent',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 12,
      },
    ]);
  });

  it('should start polling', async () => {
    mockAndFetchDiagram();

    jest.useFakeTimers();

    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        mockProcessInstanceDetailsStatistics,
      );
    });

    mockFetchProcessInstanceDetailStatistics().withSuccess(
      [
        ...mockProcessInstanceDetailsStatistics,
        {
          activityId: 'anotherNode',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
      {expectPolling: true},
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual([
        ...mockProcessInstanceDetailsStatistics,
        {
          activityId: 'anotherNode',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ]);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should stop polling when process instance is not running', async () => {
    mockAndFetchDiagram();

    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    mockFetchProcessInstanceDetailStatistics().withSuccess(
      mockProcessInstanceDetailsStatistics,
    );

    await waitFor(() => {
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        mockProcessInstanceDetailsStatistics,
      );
    });

    mockFetchProcessInstance().withSuccess(
      createInstance({id: PROCESS_INSTANCE_ID, state: 'COMPLETED'}),
    );

    expect(processInstanceDetailsStatisticsStore.intervalId).not.toBeNull();

    await processInstanceDetailsStore.fetchProcessInstance();

    expect(processInstanceDetailsStatisticsStore.intervalId).toBeNull();
  });

  it('should retry fetch on network reconnection', async () => {
    mockAndFetchDiagram();

    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() =>
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        mockProcessInstanceDetailsStatistics,
      ),
    );

    mockFetchProcessInstanceDetailStatistics().withSuccess([
      ...mockProcessInstanceDetailsStatistics,
      {
        activityId: 'anotherNode',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    eventListeners.online();

    await waitFor(() =>
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual([
        ...mockProcessInstanceDetailsStatistics,
        {
          activityId: 'anotherNode',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ]),
    );

    window.addEventListener = originalEventListener;
  });

  it('should not display counts for sub processes', async () => {
    const subProcessStatistics = [
      {
        activityId: 'service-task-1',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'service-task-2',
        active: 2,
        canceled: 0,
        incidents: 2,
        completed: 1,
      },
      {
        activityId: 'event-subprocess',
        active: 4,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'subprocess',
        active: 4,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ];

    mockFetchProcessInstanceDetailStatistics().withSuccess(
      subProcessStatistics,
    );

    mockFetchProcessXML().withSuccess(mockSubProcesses);

    mockFetchProcessInstance().withSuccess(
      createInstance({id: PROCESS_INSTANCE_ID, state: 'INCIDENT'}),
    );

    processInstanceDetailsStore.fetchProcessInstance(PROCESS_INSTANCE_ID);
    processInstanceDetailsDiagramStore.fetchProcessXml(PROCESS_INSTANCE_ID);

    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
    });

    await waitFor(() => {
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        subProcessStatistics,
      );
    });

    expect(processInstanceDetailsStatisticsStore.flowNodeStatistics).toEqual([
      {count: 2, flowNodeId: 'service-task-1', flowNodeState: 'active'},
      {count: 1, flowNodeId: 'service-task-1', flowNodeState: 'completed'},
      {count: 2, flowNodeId: 'service-task-2', flowNodeState: 'active'},
      {count: 2, flowNodeId: 'service-task-2', flowNodeState: 'incidents'},
      {count: 1, flowNodeId: 'service-task-2', flowNodeState: 'completed'},
    ]);
  });

  it('should return selectable flow nodes', async () => {
    mockAndFetchDiagram();

    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'startEvent1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'serviceTask1',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(processInstanceDetailsStatisticsStore.selectableFlowNodes).toEqual(
        ['startEvent1', 'serviceTask1'],
      );
    });
  });
});
