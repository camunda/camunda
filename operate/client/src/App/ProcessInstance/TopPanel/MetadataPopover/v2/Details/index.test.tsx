/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {type V2MetaDataDto} from '../types';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {baseMetaData, TestWrapper} from './mocks';
import {Details} from './index';
import {getExecutionDuration} from '../../Details/getExecutionDuration';

describe('MetadataPopover <Details />', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should render element instance details', () => {
    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.getByText('Details')).toBeInTheDocument();
    expect(screen.getByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('123456789')).toBeInTheDocument();
  });

  it('should display job retries when available', () => {
    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.getByText('Retries Left')).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('3');
  });

  it('should hide job retries when null', () => {
    const meta = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        jobRetries: null,
      },
    };

    render(<Details metaData={meta} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
  });

  it('should show metadata dialog when "Show more metadata" is clicked', async () => {
    const {user} = render(
      <Details metaData={baseMetaData} elementId="Task_1" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));
    expect(
      screen.getByText(/Element "Task_1" 123456789 Metadata/),
    ).toBeInTheDocument();
  });

  it('should handle null instance metadata gracefully', () => {
    const meta: V2MetaDataDto = {
      flowNodeInstanceId: null,
      flowNodeId: null,
      flowNodeType: null,
      instanceCount: null,
      instanceMetadata: null,
      incident: null,
      incidentCount: 0,
    };

    render(<Details metaData={meta} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

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

    const meta: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        type: 'USER_TASK',
      },
    };

    render(<Details metaData={meta} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    const link = screen.getByRole('link', {name: 'Open Tasklist'});
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://tasklist.example.com');
  });

  it('should not render Tasklist link for non-user tasks', () => {
    const tasklistUrl = 'https://tasklist.example.com';
    vi.stubGlobal('clientConfig', {tasklistUrl});

    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('should display execution duration info', () => {
    render(<Details metaData={baseMetaData} elementId="Task_1" />, {
      wrapper: TestWrapper,
    });

    const calculatedExecutionDuration = getExecutionDuration(
      baseMetaData!.instanceMetadata!.startDate,
      baseMetaData!.instanceMetadata!.endDate,
    );

    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
    expect(screen.getByText(calculatedExecutionDuration)).toBeInTheDocument();
  });

  it('should display user task metadata in modal when available', async () => {
    const userTaskMetaData: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        type: 'USER_TASK',
        assignee: 'john.doe',
        dueDate: '2023-12-31T23:59:59.000Z',
        followUpDate: '2023-12-30T12:00:00.000Z',
        formKey: 'user-form-key',
        userTaskKey: 'ut-123456',
        candidateGroups: ['managers', 'admins'],
        candidateUsers: ['user1', 'user2'],
        externalFormReference: 'external-form-ref-123',
        creationDate: '2023-12-01T09:00:00.000Z',
        completionDate: '2023-12-31T18:00:00.000Z',
        customHeaders: {custom1: 'value1', custom2: 2},
        priority: 10,
      },
    };

    const {user} = render(
      <Details metaData={userTaskMetaData} elementId="UserTask_1" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      screen.getByText(/Element "UserTask_1" 123456789 Metadata/),
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
    const partialUserTaskMetaData: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        type: 'USER_TASK',
        assignee: 'jane.smith',
        formKey: 'simple-form',
        userTaskKey: 'ut-789',
        dueDate: undefined,
        followUpDate: undefined,
        candidateGroups: undefined,
        candidateUsers: undefined,
        externalFormReference: undefined,
      },
    };

    const {user} = render(
      <Details metaData={partialUserTaskMetaData} elementId="UserTask_2" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.getByText(/"assignee": "jane.smith"/)).toBeInTheDocument();
    expect(screen.getByText(/"formKey": "simple-form"/)).toBeInTheDocument();
    expect(screen.getByText(/"userTaskKey": "ut-789"/)).toBeInTheDocument();
  });

  it('should not display user task fields for non-user task types', async () => {
    const serviceTaskMetaData: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        type: 'SERVICE_TASK',
      },
    };

    const {user} = render(
      <Details metaData={serviceTaskMetaData} elementId="ServiceTask_1" />,
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

  it('should display incident fields for when incident is occured', async () => {
    const incidentMetaData: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        incidentKey: '2251799813696584',
      },
      incident: {
        errorType: {
          id: 'EXTRACT_VALUE_ERROR',
          name: 'Extract value error',
        },
        errorMessage:
          "Expected result of the expression 'approverGroups' to be 'ARRAY', but was 'NULL'.",
      },
    };

    const {user} = render(
      <Details metaData={incidentMetaData} elementId="Activity_11ptrz9" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(
      screen.getByText(/"incidentKey": "2251799813696584"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"incidentErrorType": "Extract value error"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /"incidentErrorMessage": "Expected result of the expression 'approverGroups' to be 'ARRAY', but was 'NULL'."/,
      ),
    ).toBeInTheDocument();
  });

  it('should display called process fields for called instances', async () => {
    const incidentMetaData: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        calledProcessInstanceId: '229843728748927482',
        calledProcessDefinitionName: 'Called Process',
      },
    };

    const {user} = render(
      <Details metaData={incidentMetaData} elementId="Activity_11ptrz9" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.getByText(/"calledProcessInstanceKey"/)).toBeInTheDocument();
    expect(
      screen.getByText(/"calledProcessDefinitionName"/),
    ).toBeInTheDocument();
  });

  it('should display job data fields', async () => {
    const incidentMetaData: V2MetaDataDto = {
      ...baseMetaData,
      instanceMetadata: {
        ...baseMetaData.instanceMetadata!,
        jobType: 'httpService',
        jobWorker: 'worker-1',
        jobDeadline: '2023-01-15T10:10:00.000Z',
        jobCustomHeaders: {timeout: '30s'},
        jobKey: '555666777',
      },
    };

    const {user} = render(
      <Details metaData={incidentMetaData} elementId="Activity_11ptrz9" />,
      {wrapper: TestWrapper},
    );

    await user.click(screen.getByRole('button', {name: 'Show more metadata'}));

    expect(screen.getByText(/"jobKey"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobDeadline"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobType"/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker"/)).toBeInTheDocument();
  });
});
