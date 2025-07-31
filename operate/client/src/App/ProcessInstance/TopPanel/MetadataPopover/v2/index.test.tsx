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
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
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
  incidentsByProcessKeyMetadata,
  jobMetadata,
} from 'modules/mocks/metadata';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {labels, renderPopover} from './mocks';
import {
  type ProcessInstance,
  type ElementInstance,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {init} from 'modules/utils/flowNodeMetadata';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {waitFor} from '@testing-library/react';
import {mockSearchIncidents} from 'modules/mocks/api/v2/incidents/searchIncidents';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchUserTasks} from 'modules/mocks/api/v2/userTasks/searchUserTasks';

const MOCK_EXECUTION_DATE = '21 seconds';

vi.mock('date-fns', async () => {
  const actual = await vi.importActual('date-fns');
  return {
    ...actual,
    formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
  };
});

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
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionId: 'process-def-1',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

describe('MetadataPopover', () => {
  beforeEach(() => {
    init('process-instance', []);
    flowNodeSelectionStore.init();
    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockElementInstance,
    );
    mockSearchIncidents().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchElementInstances().withSuccess({
      items: [mockElementInstance],
      page: {totalItems: 1},
    });

    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchUserTasks().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: FLOW_NODE_ID,
          active: 1,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
        {
          elementId: CALL_ACTIVITY_FLOW_NODE_ID,
          active: 1,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
        {
          elementId: USER_TASK_FLOW_NODE_ID,
          active: 1,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    });
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    incidentsStore.reset();
  });

  it('should not show unrelated data', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    flowNodeMetaDataStore.setMetaData(singleInstanceMetadata);

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
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess(
      incidentsByProcessKeyMetadata,
    );
    flowNodeMetaDataStore.setMetaData(incidentFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    selectFlowNode(
      {},
      {flowNodeId: FLOW_NODE_ID, flowNodeInstanceId: '2251799813699889'},
    );

    renderPopover();

    expect(
      await screen.findByText(labels.elementInstanceKey),
    ).toBeInTheDocument();
    expect(await screen.findByText(labels.incident)).toBeInTheDocument();
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
    ).toBeInTheDocument();
  });

  it('should render meta data modal', async () => {
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);
    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      startDate: '2018-12-12 00:00:00',
      endDate: '2018-12-12 00:00:00',
    });
    flowNodeMetaDataStore.setMetaData(calledInstanceMetadata);

    mockSearchProcessInstances().withSuccess({
      items: [
        {
          ...mockProcessInstance,
          processInstanceKey: '229843728748927482',
          processDefinitionName: 'Called Process',
        },
      ],
      page: {totalItems: 1},
    });

    mockSearchJobs().withSuccess({
      items: [jobMetadata],
      page: {
        totalItems: 1,
      },
    });

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
        flowNodeInstanceId: '2251799813699889',
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
    expect(
      screen.getByText(/"jobKey": "2251799813939822"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"jobType": "io.camunda.zeebe:userTask"/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"jobRetries": 1/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker": ""/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders": {}/)).toBeInTheDocument();
    expect(screen.getByText(/"incidentErrorType": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"incidentErrorMessage": null/),
    ).toBeInTheDocument();
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
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);
    flowNodeMetaDataStore.setMetaData(multiInstancesMetadata);
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: FLOW_NODE_ID,
          active: 7,
          completed: 0,
          canceled: 0,
          incidents: 3,
        },
      ],
    });
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [
        {
          processDefinitionId: 'invoice',
          errorType: 'CALLED_ELEMENT_ERROR',
          errorMessage: 'Multi-instance incident 1',
          elementId: FLOW_NODE_ID,
          creationTime: '2022-02-03T16:44:06.981+0000',
          state: 'PENDING',
          tenantId: '<default>',
          incidentKey: '2251799814080731',
          processDefinitionKey: '2251799813686633',
          processInstanceKey: PROCESS_INSTANCE_ID,
          elementInstanceKey: '2251799813699880',
          jobKey: '2251799814080731',
        },
        {
          processDefinitionId: 'invoice',
          errorType: 'JOB_NO_RETRIES',
          errorMessage: 'Multi-instance incident 2',
          elementId: FLOW_NODE_ID,
          creationTime: '2022-02-03T16:45:06.981+0000',
          state: 'PENDING',
          tenantId: '<default>',
          incidentKey: '2251799814080732',
          processDefinitionKey: '2251799813686633',
          processInstanceKey: PROCESS_INSTANCE_ID,
          elementInstanceKey: '2251799813699881',
          jobKey: '2251799814080732',
        },
        {
          processDefinitionId: 'invoice',
          errorType: 'IO_MAPPING_ERROR',
          errorMessage: 'Multi-instance incident 3',
          elementId: FLOW_NODE_ID,
          creationTime: '2022-02-03T16:46:06.981+0000',
          state: 'PENDING',
          tenantId: '<default>',
          incidentKey: '2251799814080733',
          processDefinitionKey: '2251799813686633',
          processInstanceKey: PROCESS_INSTANCE_ID,
          elementInstanceKey: '2251799813699882',
          jobKey: '2251799814080733',
        },
      ],
      page: {totalItems: 3},
    });

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

    await waitFor(() => {
      expect(
        screen.getByText(/This Element instance triggered 10 times/),
      ).toBeInTheDocument();
    });
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
      screen.queryByText(labels.elementInstanceKey),
    ).not.toBeInTheDocument();
  });

  it('should not render called instances for multi instance call activities', async () => {
    mockFetchFlowNodeMetadata().withSuccess(multiInstanceCallActivityMetadata);
    mockFetchFlowNodeMetadata().withSuccess(multiInstanceCallActivityMetadata);
    flowNodeMetaDataStore.setMetaData(multiInstanceCallActivityMetadata);

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
      await screen.findByText(labels.elementInstanceKey),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();
  });

  //TODO fix when #35528 ready
  it.skip('should not render root cause instance link when instance is root', async () => {
    const {rootCauseInstance} = rootIncidentFlowNodeMetaData.incident;

    mockFetchFlowNodeMetadata().withSuccess(rootIncidentFlowNodeMetaData);
    flowNodeMetaDataStore.setMetaData(rootIncidentFlowNodeMetaData);

    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    selectFlowNode(
      {},
      {flowNodeId: FLOW_NODE_ID, flowNodeInstanceId: '2251799813699889'},
    );

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

    vi.stubGlobal('clientConfig', {tasklistUrl});

    mockFetchFlowNodeMetadata().withSuccess(userTaskFlowNodeMetaData);
    mockFetchFlowNodeMetadata().withSuccess(userTaskFlowNodeMetaData);
    flowNodeMetaDataStore.setMetaData(userTaskFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: USER_TASK_FLOW_NODE_ID,
        flowNodeInstanceId: '2251799813699889',
      },
    );

    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });
    renderPopover();

    expect(
      await screen.findByRole('link', {name: 'Open Tasklist'}),
    ).toHaveAttribute('href', tasklistUrl);
  });

  it('should render retries left', async () => {
    mockFetchFlowNodeMetadata().withSuccess(retriesLeftFlowNodeMetaData);
    mockFetchFlowNodeMetadata().withSuccess(retriesLeftFlowNodeMetaData);
    flowNodeMetaDataStore.setMetaData(retriesLeftFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: USER_TASK_FLOW_NODE_ID,
        flowNodeInstanceId: '2251799813699889',
      },
    );

    renderPopover();

    mockSearchJobs().withSuccess({
      items: [jobMetadata],
      page: {
        totalItems: 1,
      },
    });

    expect(await screen.findByText(labels.retriesLeft)).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('1');
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
});
