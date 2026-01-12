/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {
  mockElementInstance,
  mockJob,
  mockCalledProcessInstance,
  mockBusinessObject,
  TestWrapper,
} from './mocks';
import {Details} from './index';
import {getExecutionDuration} from './getExecutionDuration';
import {mockSearchIncidentsByElementInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByElementInstance';
import type {
  Incident,
  ElementInstance,
  UserTask,
  MessageSubscription,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {PROCESS_INSTANCE_ID} from 'modules/mocks/metadata';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchUserTasks} from 'modules/mocks/api/v2/userTasks/searchUserTasks';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchMessageSubscriptions} from 'modules/mocks/api/v2/messageSubscriptions/searchMessageSubscriptions';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';

const mockSingleIncident: Incident = {
  incidentKey: '2251799813696584',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: '2222222222222222',
  processDefinitionId: 'testProcess',
  errorType: 'EXTRACT_VALUE_ERROR',
  errorMessage:
    "Expected result of the expression 'approverGroups' to be 'ARRAY', but was 'NULL'.",
  elementId: 'Activity_0zqism7',
  elementInstanceKey: '222222222222222',
  jobKey: '33333333333333333',
  creationTime: '2024-10-28T10:00:00.000Z',
  state: 'ACTIVE',
  tenantId: '<default>',
};

describe('MetadataPopover <Details />', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchUserTasks().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchMessageSubscriptions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  it('should render element instance details', () => {
    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.getByText('Details')).toBeInTheDocument();
    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('123456789')).toBeInTheDocument();
  });

  it('should display job retries when available', async () => {
    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(await screen.findByText('Retries Left')).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('3');
  });

  it('should hide job retries when when there is no job', () => {
    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
  });

  it('should handle null instance metadata gracefully', () => {
    const minimalElementInstance: ElementInstance = {
      elementInstanceKey: '999',
      elementId: 'Task_1',
      elementName: 'Task 1',
      type: 'SERVICE_TASK',
      state: 'ACTIVE',
      startDate: '',
      processDefinitionId: 'test',
      processInstanceKey: '111',
      processDefinitionKey: '222',
      hasIncident: false,
      tenantId: '<default>',
    };

    render(
      <Details
        elementInstance={minimalElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Called Process Instance'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText('Called Decision Instance'),
    ).not.toBeInTheDocument();
  });

  it('should render Tasklist link for user tasks when configured', () => {
    const tasklistUrl = 'https://tasklist.example.com';
    vi.stubGlobal('clientConfig', {tasklistUrl});

    const userTaskInstance: ElementInstance = {
      ...mockElementInstance,
      type: 'USER_TASK',
    };

    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={userTaskInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    const link = screen.getByRole('link', {name: 'Open Tasklist'});
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://tasklist.example.com');
  });

  it('should not render Tasklist link for non-user tasks', () => {
    const tasklistUrl = 'https://tasklist.example.com';
    vi.stubGlobal('clientConfig', {tasklistUrl});

    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('should display execution duration info', () => {
    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    const calculatedExecutionDuration = getExecutionDuration(
      mockElementInstance.startDate,
      mockElementInstance.endDate,
    );

    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
    expect(screen.getByText(calculatedExecutionDuration)).toBeInTheDocument();
  });

  it('should display job worker when available', async () => {
    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(await screen.findByText('Job Worker')).toBeInTheDocument();
    expect(screen.getByText('worker-1')).toBeInTheDocument();
  });

  it('should display job type when available', async () => {
    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(await screen.findByText('Job Type')).toBeInTheDocument();
    expect(screen.getByText('httpService')).toBeInTheDocument();
  });

  it('should display job key when available', async () => {
    mockSearchJobs().withSuccess({items: [mockJob], page: {totalItems: 1}});

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(await screen.findByText('Job Key')).toBeInTheDocument();
    expect(screen.getByText('555666777')).toBeInTheDocument();
  });

  it('should display start date when available', () => {
    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.getByText('Start Date')).toBeInTheDocument();
  });

  it('should display end date when available', () => {
    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.getByText('End Date')).toBeInTheDocument();
  });

});
