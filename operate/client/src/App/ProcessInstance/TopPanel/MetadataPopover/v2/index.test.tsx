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
  calledDecisionInstanceMetadata,
} from 'modules/mocks/metadata';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {labels, renderPopover} from './mocks';
import {
  type ProcessInstance,
  type ElementInstance,
  type DecisionInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
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
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockSearchMessageSubscriptions} from 'modules/mocks/api/v2/messageSubscriptions/searchMessageSubscriptions';
import {mockFetchDecisionDefinition} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinition';

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

const mockDecisionInstance: DecisionInstance = {
  decisionEvaluationInstanceKey: '2251799813685591',
  decisionEvaluationKey: '2251799813685591',
  decisionDefinitionName: 'Test Decision',
  decisionDefinitionId: 'decision-1',
  decisionDefinitionKey: '123',
  decisionDefinitionVersion: 1,
  decisionDefinitionType: 'DECISION_TABLE',
  processDefinitionKey: '2',
  processInstanceKey: PROCESS_INSTANCE_ID,
  elementInstanceKey: '2251799813699889',
  state: 'EVALUATED',
  evaluationDate: '2018-12-12T22:00:00.000+0000',
  evaluationFailure: '',
  tenantId: '<default>',
  result: '',
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

    mockSearchMessageSubscriptions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockFetchDecisionDefinition('123456').withSuccess({
      decisionDefinitionKey: '123456',
      decisionDefinitionId: 'approval-decision',
      name: 'Approval Rules',
      version: 1,
      decisionRequirementsKey: '456789',
      decisionRequirementsId: 'approval-requirements',
      tenantId: '<default>',
    });
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    incidentsStore.reset();
  });

  it('should not show unrelated data', async () => {
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

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
      type: 'CALL_ACTIVITY',
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

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
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
      await screen.findByText(/"elementId": "Activity_0zqism7"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"elementInstanceKey": "2251799813699889"/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"type": "CALL_ACTIVITY"/)).toBeInTheDocument();
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

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

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

  //TODO fix when #35529 ready
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

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

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

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

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

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
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
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

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

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
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
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });
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
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });
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

  //TODO fix when #35528 ready
  it.skip('should render root cause decision instance link when decision instance exists', async () => {
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      hasIncident: true,
    });
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess(
      incidentsByProcessKeyMetadata,
    );
    mockSearchDecisionInstances().withSuccess({
      items: [mockDecisionInstance],
      page: {totalItems: 1},
    });
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
      await screen.findByText('Root Cause Decision Instance'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: /View root cause decision Test Decision - 2251799813685591/,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Test Decision - 2251799813685591'),
    ).toBeInTheDocument();
  });

  //TODO fix when #35528 ready
  it.skip('should not render root cause decision instance when no decision instance exists', async () => {
    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess(
      incidentsByProcessKeyMetadata,
    );
    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
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

    expect(await screen.findByText(labels.incident)).toBeInTheDocument();
    expect(
      screen.queryByText('Root Cause Decision Instance'),
    ).not.toBeInTheDocument();
  });

  it('should render called decision instance link for business rule task', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      elementId: 'BusinessRuleTask',
      type: 'BUSINESS_RULE_TASK',
    });
    const rootDecisionInstance = {
      ...calledDecisionInstanceMetadata,
      rootDecisionDefinitionKey: '123456',
    };

    mockSearchDecisionInstances().withSuccess({
      items: [rootDecisionInstance],
      page: {totalItems: 1},
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {flowNodeId: 'BusinessRuleTask', flowNodeInstanceId: '2251799813699889'},
    );

    renderPopover();

    await waitFor(() => {
      expect(screen.getByText('Called Decision Instance')).toBeInTheDocument();
    });

    expect(
      screen.getByRole('link', {
        name: /View Approval Rules instance 9876543210/,
      }),
    ).toBeInTheDocument();
    expect(screen.getByText('Approval Rules - 9876543210')).toBeInTheDocument();
  });

  it('should not show decision instances for non-business rule tasks', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      type: 'SERVICE_TASK',
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {flowNodeId: FLOW_NODE_ID, flowNodeInstanceId: '2251799813699889'},
    );

    renderPopover();

    await waitFor(() => {
      expect(
        screen.getByRole('heading', {name: labels.details}),
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByText('Called Decision Instance'),
    ).not.toBeInTheDocument();
  });

  it('should render called decision definition name when no decision instances exist', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      type: 'BUSINESS_RULE_TASK',
    });

    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {flowNodeId: FLOW_NODE_ID, flowNodeInstanceId: '2251799813699889'},
    );

    renderPopover();

    await waitFor(() => {
      expect(
        screen.getByRole('heading', {name: labels.details}),
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByText('Called Decision Instance'),
    ).not.toBeInTheDocument();
  });

  it('should handle decision instance search errors gracefully', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchElementInstance('2251799813699889').withSuccess({
      ...mockElementInstance,
      type: 'BUSINESS_RULE_TASK',
    });

    mockSearchDecisionInstances().withNetworkError();

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    selectFlowNode(
      {},
      {flowNodeId: FLOW_NODE_ID, flowNodeInstanceId: '2251799813699889'},
    );

    renderPopover();

    expect(
      screen.queryByRole('heading', {name: labels.details}),
    ).not.toBeInTheDocument();
  });
});
