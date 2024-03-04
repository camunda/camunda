/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
      {count: 1, flowNodeId: 'exclusiveGateway', flowNodeState: 'incidents'},
      {count: 2, flowNodeId: 'exclusiveGateway', flowNodeState: 'canceled'},
      {count: 4, flowNodeId: 'alwaysFailingTask', flowNodeState: 'incidents'},
      {count: 5, flowNodeId: 'messageCatchEvent', flowNodeState: 'active'},
      {count: 1, flowNodeId: 'messageCatchEvent', flowNodeState: 'incidents'},
      {count: 12, flowNodeId: 'endEvent', flowNodeState: 'completed'},
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

  it('should start polling', async () => {
    mockAndFetchDiagram();

    jest.useFakeTimers();

    processInstanceDetailsStatisticsStore.init(PROCESS_INSTANCE_ID);

    await waitFor(() => {
      expect(processInstanceDetailsStatisticsStore.state.statistics).toEqual(
        mockProcessInstanceDetailsStatistics,
      );
    });

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
      {count: 2, flowNodeId: 'service-task-2', flowNodeState: 'active'},
      {count: 2, flowNodeId: 'service-task-2', flowNodeState: 'incidents'},
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
