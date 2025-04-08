/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
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
import {labels, renderPopover} from './mocks';

const MOCK_EXECUTION_DATE = '21 seconds';

jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
}));

describe('MetadataPopover', () => {
  beforeEach(() => {
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should not show unrelated data', async () => {
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: FLOW_NODE_ID,
    });

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

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

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
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    const {user} = renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: labels.showMoreMetadata}),
    );

    expect(
      screen.getByText(
        /Flow Node "Activity_0zqism7" 2251799813699889 Metadata/,
      ),
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
        /Flow Node "Activity_0zqism7" 2251799813699889 Metadata/,
      ),
    ).not.toBeInTheDocument();
  });

  it('should render metadata for multi instance flow nodes', async () => {
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/This Flow Node triggered 10 times/),
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
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

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

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(labels.rootCauseProcessInstance),
    ).toBeInTheDocument();
    expect(screen.getByText(/Current Instance/)).toBeInTheDocument();
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

    flowNodeSelectionStore.selectFlowNode({flowNodeId: USER_TASK_FLOW_NODE_ID});

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

    flowNodeSelectionStore.selectFlowNode({flowNodeId: USER_TASK_FLOW_NODE_ID});

    renderPopover();

    expect(await screen.findByText(labels.retriesLeft)).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('2');
  });
});
