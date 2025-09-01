/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {labels, renderPopover} from './mocks';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledInstanceMetadata,
  calledUnevaluatedDecisionMetadata,
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
  FLOW_NODE_ID,
  USER_TASK_FLOW_NODE_ID,
  BUSSINESS_RULE_FLOW_NODE_ID,
  calledDecisionInstanceMetadata,
} from 'modules/mocks/metadata';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {
  createInstance,
  createProcessInstance,
  mockCallActivityProcessXML,
} from 'modules/testUtils';
import {init} from 'modules/utils/flowNodeMetadata';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockIncidents} from 'modules/mocks/incidents';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {incidentsStore} from 'modules/stores/incidents';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchIncidents} from 'modules/mocks/api/v2/incidents/searchIncidents';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchUserTasks} from 'modules/mocks/api/v2/userTasks/searchUserTasks';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';

import type {
  ElementInstance,
  ProcessInstance,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';

const MOCK_EXECUTION_DATE = '21 seconds';

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: BUSSINESS_RULE_FLOW_NODE_ID,
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2018-06-21T10:00:00.000Z',
  endDate: '2018-06-21T10:00:00.000Z',
  processDefinitionId: 'someKey',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: '229843728748927482',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'Called Process',
  hasIncident: true,
};

vi.mock('date-fns', async () => {
  const actual = await vi.importActual('date-fns');
  return {
    ...actual,
    formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
  };
});

describe('MetadataPopover', () => {
  beforeEach(() => {
    init('process-instance', []);
    flowNodeSelectionStore.init();
    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockElementInstance,
    );
    mockSearchElementInstances().withSuccess({
      items: [mockElementInstance],
      page: {totalItems: 1},
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
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchIncidents().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchJobs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchUserTasks().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    incidentsStore.reset();
  });

  it('should render meta data for completed flow node', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
    );

    const mockCallActivityElementInstance: ElementInstance = {
      ...mockElementInstance,
      elementId: CALL_ACTIVITY_FLOW_NODE_ID,
    };

    mockFetchElementInstance('2251799813699889').withSuccess(
      mockCallActivityElementInstance,
    );

    mockSearchElementInstances().withSuccess({
      items: [mockCallActivityElementInstance],
      page: {totalItems: 1},
    });

    mockSearchProcessInstances().withSuccess({
      items: [mockProcessInstance],
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
    selectFlowNode({}, {flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(labels.elementInstanceKey),
    ).toBeInTheDocument();
    expect(screen.getByText(labels.executionDuration)).toBeInTheDocument();
    expect(
      await screen.findByText(labels.calledProcessInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: labels.showMoreMetadata}),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        calledInstanceMetadata.instanceMetadata!.flowNodeInstanceId,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Less than 1 second')).toBeInTheDocument();
    expect(screen.getByTestId('called-process-instance')).toHaveTextContent(
      `Called Process - ${
        calledInstanceMetadata.instanceMetadata!.calledProcessInstanceId
      }`,
    );

    vi.clearAllTimers();
    vi.useFakeTimers();
  });

  it('should render completed decision', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const mockBusinessRuleElementInstance: ElementInstance = {
      ...mockElementInstance,
      elementId: BUSSINESS_RULE_FLOW_NODE_ID,
      type: 'BUSINESS_RULE_TASK',
    };

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledDecisionMetadata);
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
    );
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockBusinessRuleElementInstance,
    );

    mockSearchElementInstances().withSuccess({
      items: [mockBusinessRuleElementInstance],
      page: {totalItems: 1},
    });

    mockSearchDecisionInstances().withSuccess({
      items: [calledDecisionInstanceMetadata],
      page: {totalItems: 1},
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      }),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: BUSSINESS_RULE_FLOW_NODE_ID,
        flowNodeInstanceId: '2251799813699889',
      },
    );

    const {user} = renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {name: labels.incident}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${calledDecisionInstanceMetadata.decisionDefinitionName} - ${
          calledDecisionInstanceMetadata.decisionInstanceId
        }`,
      ),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${calledDecisionInstanceMetadata.decisionInstanceId}`,
      ),
    );

    vi.clearAllTimers();
    vi.useFakeTimers();
  });

  //TODO fix when #35528 ready
  it.skip('should render failed decision', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {instanceMetadata} = calledFailedDecisionMetadata;
    const {rootCauseDecision} = calledFailedDecisionMetadata!.incident!;

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledFailedDecisionMetadata);
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );

    selectFlowNode(
      {},
      {
        flowNodeId: BUSSINESS_RULE_FLOW_NODE_ID,
        flowNodeInstanceId: '2251799813699889',
      },
    );

    const {user} = renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: labels.incident}),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(labels.rootCauseDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseProcessInstance),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${rootCauseDecision!.decisionName!} - ${
          rootCauseDecision!.instanceId
        }`,
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${rootCauseDecision!.instanceId}`,
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render unevaluated decision', async () => {
    const mockBusinessRuleElementInstance: ElementInstance = {
      ...mockElementInstance,
      elementId: BUSSINESS_RULE_FLOW_NODE_ID,
      type: 'BUSINESS_RULE_TASK',
    };

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledUnevaluatedDecisionMetadata);
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
    );
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockBusinessRuleElementInstance,
    );

    mockSearchDecisionInstances().withSuccess({
      items: [calledDecisionInstanceMetadata],
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
      {
        flowNodeId: BUSSINESS_RULE_FLOW_NODE_ID,
        flowNodeInstanceId: '2251799813699889',
      },
    );

    renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        `${
          calledDecisionInstanceMetadata.decisionDefinitionName
        } - ${calledDecisionInstanceMetadata.decisionInstanceId}`,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(labels.incident)).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();
  });
});
