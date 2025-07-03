/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {incidentsStore} from 'modules/stores/incidents';
import {
  calledInstanceMetadata,
  incidentFlowNodeMetaData,
  multiInstanceCallActivityMetadata,
  multiInstancesMetadata,
  rootIncidentFlowNodeMetaData,
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
  FLOW_NODE_ID,
  userTaskFlowNodeMetaData,
  USER_TASK_FLOW_NODE_ID,
  retriesLeftFlowNodeMetaData,
  singleInstanceMetadata,
} from 'modules/mocks/metadata';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchUserTasks} from 'modules/mocks/api/v2/userTasks/searchUserTasks';
import {labels, renderPopover} from './mocks';
import {ProcessInstance, ElementInstance} from '@vzeta/camunda-api-zod-schemas';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {init} from 'modules/utils/flowNodeMetadata';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';

const MOCK_EXECUTION_DATE = '21 seconds';

jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
}));

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: PROCESS_INSTANCE_ID,
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
};

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: 'Activity_0zqism7',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2018-12-12T00:00:00.000Z',
  endDate: '2018-12-12T00:05:00.000Z',
  processDefinitionId: 'process-def-1',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const mockUserTaskElementInstance: ElementInstance = {
  ...mockElementInstance,
  type: 'USER_TASK',
  elementInstanceKey: 'userTask123',
  elementId: 'UserTask_1',
  elementName: 'User Task',
};

describe('MetadataPopover', () => {
  beforeEach(() => {
    init('process-instance', []);
    flowNodeSelectionStore.init();
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchElementInstance('').withNetworkError();
    mockSearchElementInstances().withNetworkError();
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockSearchUserTasks().withNetworkError();
  });

  it('should not show unrelated data', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: labels.incidents}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {name: labels.incident}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledDecisionInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseProcessInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(labels.retriesLeft)).not.toBeInTheDocument();
    expect(screen.queryByText(labels.type)).not.toBeInTheDocument();
    expect(screen.queryByText(labels.errorMessage)).not.toBeInTheDocument();
  });

  it('should render meta data for incident flow node', async () => {
    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    selectFlowNode({}, {flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(labels.flowNodeInstanceKey),
    ).toBeInTheDocument();
    expect(screen.getByText(labels.executionDuration)).toBeInTheDocument();
    expect(screen.getByText(labels.type)).toBeInTheDocument();
    expect(screen.getByText(labels.errorMessage)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: labels.showMoreMetadata,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: labels.showIncident,
      }),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();

    const {incident, instanceMetadata} = incidentFlowNodeMetaData;

    expect(
      screen.getByText(instanceMetadata!.flowNodeInstanceId),
    ).toBeInTheDocument();
    expect(
      screen.getByText(`${MOCK_EXECUTION_DATE} (running)`),
    ).toBeInTheDocument();
    expect(screen.getByText(incident.errorMessage)).toBeInTheDocument();
    expect(screen.getByText(incident.errorType.name)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${incident.rootCauseInstance.processDefinitionName} - ${incident.rootCauseInstance.instanceId}`,
      ),
    );
  });

  it('should render meta data modal', async () => {
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
      },
    );

    const {user} = renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: labels.showMoreMetadata}),
    );

    expect(
      screen.getByText(/Element "Activity_0zqism7" 2251799813699889 Metadata/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();

    expect(
      await screen.findByText(/"flowNodeId": "Activity_0zqism7"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeInstanceKey": "2251799813699889"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeType": "TASK_CALL_ACTIVITY"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"startDate": "2018-12-12 00:00:00"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"endDate": "2018-12-12 00:00:00"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"jobDeadline": "2018-12-12 00:00:00"/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"incidentErrorType": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"incidentErrorMessage": null/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"jobId": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobType": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobRetries": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"calledProcessInstanceKey": "229843728748927482"/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));
    expect(
      screen.queryByText(
        /Element "Activity_0zqism7" 2251799813699889 Metadata/,
      ),
    ).not.toBeInTheDocument();
  });

  it('should render metadata for multi instance elements', async () => {
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByText(/This Element triggered 10 times/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /To view details for any of these, select one Instance in the Instance History./,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/3 incidents occurred/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: labels.showIncidents}),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.flowNodeInstanceKey),
    ).not.toBeInTheDocument();
  });

  it('should not render called instances for multi instance call activities', async () => {
    mockFetchFlowNodeMetadata().withSuccess(multiInstanceCallActivityMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByText(labels.flowNodeInstanceKey),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();
  });

  it('should not render root cause instance link when instance is root', async () => {
    const {rootCauseInstance} = rootIncidentFlowNodeMetaData.incident;

    mockFetchFlowNodeMetadata().withSuccess(rootIncidentFlowNodeMetaData);

    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    selectFlowNode({}, {flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(labels.rootCauseProcessInstance),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Current Instance/)).toBeInTheDocument();
    expect(
      screen.queryByText(
        `${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`,
      ),
    ).not.toBeInTheDocument();
  });

  it('should render link to tasklist', async () => {
    const tasklistUrl = 'https://tasklist:8080';
    window.clientConfig = {tasklistUrl};

    mockFetchFlowNodeMetadata().withSuccess(userTaskFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode({}, {flowNodeId: USER_TASK_FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByRole('link', {name: 'Open Tasklist'}),
    ).toHaveAttribute('href', tasklistUrl);
  });

  it('should render retries left', async () => {
    mockFetchFlowNodeMetadata().withSuccess(retriesLeftFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode({}, {flowNodeId: USER_TASK_FLOW_NODE_ID});

    renderPopover();

    expect(await screen.findByText(labels.retriesLeft)).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('2');
  });

  it('should render metadata popover with element instance terminology', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    expect(screen.getByText(labels.flowNodeInstanceKey)).toBeInTheDocument();
  });

  it('should handle multiple element instances scenario', async () => {
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByText(/This Element triggered 10 times/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /To view details for any of these, select one Instance in the Instance History./,
      ),
    ).toBeInTheDocument();
  });

  it('should preserve backward compatibility with existing incident data', async () => {
    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    selectFlowNode({}, {flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(await screen.findByText(labels.type)).toBeInTheDocument();
    expect(screen.getByText(labels.errorMessage)).toBeInTheDocument();
  });

  it('should preserve call activity functionality', async () => {
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    selectFlowNode(
      {},
      {
        flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
      },
    );

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();
  });

  it('should fetch and display specific element instance when selected from history', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockElementInstance,
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
        flowNodeInstanceId: '2251799813699889',
      },
    );

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();
    expect(screen.getByText('2251799813699889')).toBeInTheDocument();
  });

  it('should search for single element instance when count is 1', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: FLOW_NODE_ID,
          active: 1,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    });
    mockSearchElementInstances().withSuccess({
      items: [mockElementInstance],
      page: {totalItems: 1},
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode({}, {flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();
    expect(screen.getByText('2251799813699889')).toBeInTheDocument();
  });

  it('should handle failed element instance search gracefully', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchElementInstances().withNetworkError();

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode({}, {flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      screen.queryByRole('heading', {name: labels.details}),
    ).not.toBeInTheDocument();
  });

  it('should handle failed single element instance fetch gracefully', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchElementInstance('invalid-key').withNetworkError();

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
        flowNodeInstanceId: 'invalid-key',
      },
    );

    renderPopover();

    expect(
      screen.queryByRole('heading', {name: labels.details}),
    ).not.toBeInTheDocument();
  });

  it('should show multi-instance message and not fetch individual instances', async () => {
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      }),
    );

    selectFlowNode({}, {flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(/This Element triggered 10 times/),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: labels.details}),
    ).not.toBeInTheDocument();
  });

  it('should fetch user task data when element instance is USER_TASK type', async () => {
    const nonUserTaskInstance = {
      ...mockUserTaskElementInstance,
      type: 'MANUAL_TASK' as const,
    };

    mockFetchElementInstance('userTask123').withSuccess(nonUserTaskInstance);

    selectFlowNode(
      {},
      {
        flowNodeId: USER_TASK_FLOW_NODE_ID,
        flowNodeInstanceId: 'userTask123',
      },
    );

    renderPopover();
    expect(mockSearchUserTasks).not.toHaveBeenCalled();
  });

  it('should not fetch user task data for non-user task elements', async () => {
    const serviceTaskInstance = {
      ...mockElementInstance,
      type: 'SERVICE_TASK' as const,
      elementInstanceKey: 'serviceTask123',
    };

    mockFetchElementInstance('serviceTask123').withSuccess(serviceTaskInstance);

    selectFlowNode(
      {},
      {
        flowNodeId: FLOW_NODE_ID,
        flowNodeInstanceId: 'serviceTask123',
      },
    );

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    expect(mockSearchUserTasks).not.toHaveBeenCalled();
  });

  it('should handle empty user task search results', async () => {
    mockSearchUserTasks().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    selectFlowNode(
      {},
      {
        flowNodeId: USER_TASK_FLOW_NODE_ID,
        flowNodeInstanceId: 'userTask123',
      },
    );

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();
  });
});
