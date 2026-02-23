/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor} from 'modules/testing-library';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {labels, renderPopover} from './mocks';
import {
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
  FLOW_NODE_ID,
  USER_TASK_FLOW_NODE_ID,
  BUSINESS_RULE_FLOW_NODE_ID,
  calledDecisionInstanceMetadata,
} from 'modules/mocks/metadata';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {
  createProcessInstance,
  mockCallActivityProcessXML,
  searchResult,
} from 'modules/testUtils';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchIncidentsByElementInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByElementInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import type {
  ElementInstance,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchMessageSubscriptions} from 'modules/mocks/api/v2/messageSubscriptions/searchMessageSubscriptions';

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: BUSINESS_RULE_FLOW_NODE_ID,
  elementName: 'Business Rule Task',
  type: 'BUSINESS_RULE_TASK',
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

const mockIncident = {
  incidentKey: '4503599627375678',
  errorType: 'DECISION_EVALUATION_ERROR' as const,
  errorMessage:
    "Failed to evaluate expression 'paid = false': no variable found for name 'paid'",
  state: 'ACTIVE' as const,
  creationTime: '2022-02-03T16:44:06.981+0000',
  processDefinitionId: 'someKey',
  processDefinitionKey: '2',
  processInstanceKey: PROCESS_INSTANCE_ID,
  elementId: BUSINESS_RULE_FLOW_NODE_ID,
  elementInstanceKey: '2251799813699889',
  jobKey: '',
  tenantId: '<default>',
};

describe('MetadataPopover', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess(
      searchResult([]),
    );
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockElementInstance,
    );
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance]),
    );
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

    mockSearchJobs().withSuccess(searchResult([]));

    mockSearchProcessInstances().withSuccess(searchResult([]));

    mockSearchIncidentsByProcessInstance('2251799813685294').withSuccess(
      searchResult([]),
    );

    mockSearchMessageSubscriptions().withSuccess(searchResult([]));

    mockSearchDecisionInstances().withSuccess(searchResult([]));

    vi.useFakeTimers({shouldAdvanceTime: true});
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render meta data for completed flow node', async () => {
    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        processInstanceKey: PROCESS_INSTANCE_ID,
      }),
    );

    const mockCallActivityElementInstance: ElementInstance = {
      ...mockElementInstance,
      elementId: CALL_ACTIVITY_FLOW_NODE_ID,
      elementName: 'Call Activity',
      type: 'CALL_ACTIVITY',
    };

    mockFetchElementInstance('2251799813699889').withSuccess(
      mockCallActivityElementInstance,
    );

    mockSearchElementInstances().withSuccess(
      searchResult([mockCallActivityElementInstance]),
    );

    mockSearchProcessInstances().withSuccess(
      searchResult([mockProcessInstance]),
    );

    mockSearchIncidentsByProcessInstance('2251799813685294').withSuccess(
      searchResult([]),
    );

    renderPopover({
      elementId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

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
      screen.getByText(mockCallActivityElementInstance.elementInstanceKey),
    ).toBeInTheDocument();
    expect(screen.getByText('Less than 1 second')).toBeInTheDocument();
    expect(screen.getByTestId('called-process-instance')).toHaveTextContent(
      `Called Process - ${mockProcessInstance.processInstanceKey}`,
    );
  });

  it('should render completed decision', async () => {
    const mockBusinessRuleElementInstance: ElementInstance = {
      ...mockElementInstance,
      elementId: BUSINESS_RULE_FLOW_NODE_ID,
      type: 'BUSINESS_RULE_TASK',
    };

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchProcessInstanceV2().withSuccess(createProcessInstance());
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockBusinessRuleElementInstance,
    );

    mockSearchDecisionInstances().withSuccess(
      searchResult([calledDecisionInstanceMetadata]),
    );

    mockSearchElementInstances().withSuccess(
      searchResult([mockBusinessRuleElementInstance]),
    );

    mockSearchIncidentsByProcessInstance('2251799813685294').withSuccess(
      searchResult([]),
    );

    const {user} = renderPopover({
      elementId: BUSINESS_RULE_FLOW_NODE_ID,
      elementInstanceKey: '2251799813699889',
    });
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
        `${calledDecisionInstanceMetadata!.decisionDefinitionName} - ${
          calledDecisionInstanceMetadata!.decisionEvaluationInstanceKey
        }`,
      ),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${calledDecisionInstanceMetadata!.decisionEvaluationInstanceKey}`,
      ),
    );
  });

  it('should render failed decision', async () => {
    const mockBusinessRuleElementInstance: ElementInstance = {
      ...mockElementInstance,
      elementId: BUSINESS_RULE_FLOW_NODE_ID,
      type: 'BUSINESS_RULE_TASK',
      hasIncident: true,
    };

    const mockFailedDecisionInstance = {
      ...calledDecisionInstanceMetadata,
      state: 'FAILED' as const,
    };

    mockFetchProcessDefinitionXml().withSuccess(metadataDemoProcess);
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
    );
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockBusinessRuleElementInstance,
    );

    mockSearchElementInstances().withSuccess(
      searchResult([mockBusinessRuleElementInstance]),
    );

    mockSearchIncidentsByProcessInstance(PROCESS_INSTANCE_ID).withSuccess(
      searchResult([mockIncident]),
    );

    mockSearchIncidentsByElementInstance('2251799813699889').withSuccess(
      searchResult([mockIncident]),
    );

    mockSearchDecisionInstances().withSuccess(
      searchResult([mockFailedDecisionInstance]),
    );

    const {user} = renderPopover({
      elementId: BUSINESS_RULE_FLOW_NODE_ID,
      elementInstanceKey: '2251799813699889',
    });

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: labels.incident}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: `View ${mockFailedDecisionInstance.decisionDefinitionName} instance ${mockFailedDecisionInstance.decisionEvaluationInstanceKey}`,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(labels.rootCauseDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseProcessInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: `View root cause decision ${mockFailedDecisionInstance.decisionDefinitionName} - ${
          mockFailedDecisionInstance.decisionEvaluationInstanceKey
        }`,
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('link', {
        name: `View root cause decision ${mockFailedDecisionInstance.decisionDefinitionName} - ${
          mockFailedDecisionInstance.decisionEvaluationInstanceKey
        }`,
      }),
    );
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${mockFailedDecisionInstance.decisionEvaluationInstanceKey}`,
      ),
    );
  });
});
