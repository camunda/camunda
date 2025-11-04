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
  });

  it('should render element instance details', () => {
    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.getByText('Details')).toBeInTheDocument();
    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('123456789')).toBeInTheDocument();
  });

  it('should display job retries when available', () => {
    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
      />,
      {
        wrapper: TestWrapper,
      },
    );

    expect(screen.getByText('Retries Left')).toBeInTheDocument();
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

  it('should show metadata dialog when "Show more metadata" is clicked', async () => {
    const {user} = render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));
    expect(
      screen.getByText(/Element "Service Task" 123456789 Metadata/),
    ).toBeInTheDocument();
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

    render(
      <Details
        elementInstance={userTaskInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
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

    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
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
    render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
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

  it('should display user task metadata in modal when available', async () => {
    const userTaskInstance: ElementInstance = {
      ...mockElementInstance,
      type: 'USER_TASK',
    };

    const userTask: UserTask = {
      userTaskKey: 'ut-123456',
      processInstanceKey: '111222333',
      processDefinitionKey: '444555666',
      processDefinitionId: 'process-def-1',
      elementId: 'Task_1',
      elementInstanceKey: '123456789',
      assignee: 'john.doe',
      state: 'CREATED',
      creationDate: '2023-12-01T09:00:00.000Z',
      completionDate: '2023-12-31T18:00:00.000Z',
      dueDate: '2023-12-31T23:59:59.000Z',
      followUpDate: '2023-12-30T12:00:00.000Z',
      tenantId: '<default>',
      formKey: 'user-form-key',
      candidateGroups: ['managers', 'admins'],
      candidateUsers: ['user1', 'user2'],
      externalFormReference: 'external-form-ref-123',
      customHeaders: {custom1: 'value1', custom2: 2},
      priority: 10,
      processDefinitionVersion: 1,
    };

    const {user} = render(
      <Details
        elementInstance={userTaskInstance}
        businessObject={mockBusinessObject}
        userTask={userTask}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      screen.getByText(/Element "Service Task" 123456789 Metadata/),
    ).toBeInTheDocument();

    expect(screen.getByText(/"assignee": "john.doe"/)).toBeInTheDocument();
    expect(
      screen.getByText(/"dueDate": "2023-12-31T23:59:59.000Z"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"followUpDate": "2023-12-30T12:00:00.000Z"/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"formKey": "user-form-key"/)).toBeInTheDocument();
    expect(screen.getByText(/"userTaskKey": "ut-123456"/)).toBeInTheDocument();
    expect(
      screen.getByText(
        (content) =>
          content.includes('"candidateGroups":') &&
          content.includes('"managers"') &&
          content.includes('"admins"'),
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        (content) =>
          content.includes('"candidateUsers":') &&
          content.includes('"user1"') &&
          content.includes('"user2"'),
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"externalFormReference": "external-form-ref-123"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"creationDate": "2023-12-01T09:00:00.000Z"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"completionDate": "2023-12-31T18:00:00.000Z"/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"priority": 10/)).toBeInTheDocument();
    expect(
      screen.getByText(
        (content) =>
          content.includes('"customHeaders":') &&
          content.includes('"custom1": "value1"') &&
          content.includes('"custom2": 2'),
      ),
    ).toBeInTheDocument();
  });

  it('should display partial user task metadata when some fields are missing', async () => {
    const userTaskInstance: ElementInstance = {
      ...mockElementInstance,
      type: 'USER_TASK',
    };

    const partialUserTask: UserTask = {
      userTaskKey: 'ut-789',
      processInstanceKey: '111222333',
      processDefinitionKey: '444555666',
      processDefinitionId: 'process-def-1',
      elementId: 'Task_1',
      elementInstanceKey: '123456789',
      assignee: 'jane.smith',
      state: 'CREATED',
      creationDate: '2023-12-01T09:00:00.000Z',
      tenantId: '<default>',
      formKey: 'simple-form',
      processDefinitionVersion: 1,
      candidateGroups: [],
      candidateUsers: [],
      priority: 0,
    };

    const {user} = render(
      <Details
        elementInstance={userTaskInstance}
        businessObject={mockBusinessObject}
        userTask={partialUserTask}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.getByText(/"assignee": "jane.smith"/)).toBeInTheDocument();
    expect(screen.getByText(/"formKey": "simple-form"/)).toBeInTheDocument();
    expect(screen.getByText(/"userTaskKey": "ut-789"/)).toBeInTheDocument();
  });

  it('should not display user task fields for non-user task types', async () => {
    const {user} = render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.queryByText(/"assignee"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"dueDate"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"followUpDate"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"formKey"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"userTaskKey"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"candidateGroups"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"candidateUsers"/)).not.toBeInTheDocument();
    expect(
      screen.queryByText(/"externalFormReference"/),
    ).not.toBeInTheDocument();
  });

  it('should display incident fields for when incident has occurred', async () => {
    const incidentElementInstance: ElementInstance = {
      ...mockElementInstance,
      hasIncident: true,
    };

    mockSearchIncidentsByElementInstance('123456789').withSuccess({
      items: [mockSingleIncident],
      page: {totalItems: 1},
    });

    const {user} = render(
      <Details
        elementInstance={incidentElementInstance}
        businessObject={mockBusinessObject}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      await screen.findByText(/"incidentErrorType": "Extract value error."/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /"incidentErrorMessage": "Expected result of the expression 'approverGroups' to be 'ARRAY', but was 'NULL'."/,
      ),
    ).toBeInTheDocument();
  });

  it('should display called process fields for called instances', async () => {
    const callActivityBusinessObject: BusinessObject = {
      ...mockBusinessObject,
      $type: 'bpmn:CallActivity',
    };

    const {user} = render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={callActivityBusinessObject}
        calledProcessInstance={mockCalledProcessInstance}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.getByText(/"calledProcessInstanceKey"/)).toBeInTheDocument();
    expect(
      screen.getByText(/"calledProcessDefinitionName"/),
    ).toBeInTheDocument();
  });

  it('should display job data fields', async () => {
    const {user} = render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.getByText(/"jobKey"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobDeadline"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobType"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker"/)).toBeInTheDocument();
  });

  it('should display message name and correlation key for service task with message subscription data', async () => {
    const messageSubscription: MessageSubscription = {
      messageSubscriptionKey: 'msg-sub-123',
      messageName: 'orderReceived',
      correlationKey: 'order-123',
      processInstanceKey: '111222333',
      processDefinitionKey: '444555666',
      processDefinitionId: 'process-def-1',
      elementId: 'Task_1',
      elementInstanceKey: '123456789',
      messageSubscriptionState: 'CREATED',
      lastUpdatedDate: '2023-01-15T10:00:00.000Z',
      tenantId: '<default>',
    };

    const {user} = render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        messageSubscription={messageSubscription}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      screen.getByText(/Element "Service Task" 123456789 Metadata/),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/"messageName": "orderReceived"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"correlationKey": "order-123"/),
    ).toBeInTheDocument();
  });

  it('should not display message fields when message name and correlation key are absent', async () => {
    const {user} = render(
      <Details
        elementInstance={mockElementInstance}
        businessObject={mockBusinessObject}
        job={mockJob}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.queryByText(/"messageName"/)).not.toBeInTheDocument();
    expect(screen.queryByText(/"correlationKey"/)).not.toBeInTheDocument();
  });

  it('should display message subscription data for message events', async () => {
    const eventElementInstance: ElementInstance = {
      ...mockElementInstance,
      type: 'INTERMEDIATE_CATCH_EVENT',
    };

    const messageSubscription: MessageSubscription = {
      messageSubscriptionKey: 'msg-sub-456',
      messageName: 'clientMessage',
      correlationKey: 'client-456',
      processInstanceKey: '111222333',
      processDefinitionKey: '444555666',
      processDefinitionId: 'process-def-1',
      elementId: 'Task_1',
      elementInstanceKey: '123456789',
      messageSubscriptionState: 'CREATED',
      lastUpdatedDate: '2023-01-15T10:00:00.000Z',
      tenantId: '<default>',
    };

    const {user} = render(
      <Details
        elementInstance={eventElementInstance}
        businessObject={mockBusinessObject}
        messageSubscription={messageSubscription}
      />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      screen.getByText(/Element "Service Task" 123456789 Metadata/),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/"messageName": "clientMessage"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"correlationKey": "client-456"/),
    ).toBeInTheDocument();
  });
});
