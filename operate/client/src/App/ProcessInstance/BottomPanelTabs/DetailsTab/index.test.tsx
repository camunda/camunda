/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {createMemoryRouter, RouterProvider} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {DetailsTab} from './index';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockSearchUserTasks} from 'modules/mocks/api/v2/userTasks/searchUserTasks';
import {searchResult} from 'modules/testUtils';
import * as clientConfig from 'modules/utils/getClientConfig';
import type {
  ElementInstance,
  Job,
  ProcessInstance,
  DecisionInstance,
  UserTask,
} from '@camunda/camunda-api-zod-schemas/8.10';

const PROCESS_INSTANCE_ID = '111222333';
const PROCESS_DEFINITION_KEY = '444555666';

const CALL_ACTIVITY_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:callActivity id="Task_1" name="Call Activity" />
  </bpmn:process>
</bpmn:definitions>`;

const BUSINESS_RULE_TASK_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:businessRuleTask id="Task_1" name="Business Rule Task" />
  </bpmn:process>
</bpmn:definitions>`;

const USER_TASK_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:userTask id="Task_1" name="User Task" />
  </bpmn:process>
</bpmn:definitions>`;

const CAMUNDA_USER_TASK_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:userTask id="Task_1" name="User Task">
      <bpmn:extensionElements>
        <zeebe:userTask />
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`;

const mockElementInstance = {
  elementInstanceKey: '123456789',
  elementId: 'Task_1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2023-01-15T10:00:00.000Z',
  endDate: '2023-01-15T10:05:00.000Z',
  processDefinitionId: 'process-def-1',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  hasIncident: false,
  tenantId: '<default>',
  rootProcessInstanceKey: null,
  incidentKey: null,
} satisfies ElementInstance;

const mockJob = {
  jobKey: '555666777',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  processDefinitionId: 'process-def-1',
  elementId: 'Task_1',
  elementInstanceKey: '123456789',
  type: 'httpService',
  worker: 'worker-1',
  retries: 3,
  deadline: '2023-01-15T10:10:00.000Z',
  customHeaders: {timeout: '30s'},
  state: 'CREATED',
  tenantId: '<default>',
  kind: 'BPMN_ELEMENT',
  listenerEventType: 'UNSPECIFIED',
  isDenied: false,
  deniedReason: '',
  hasFailedWithRetriesLeft: false,
  errorCode: '',
  errorMessage: '',
  endTime: '',
  rootProcessInstanceKey: null,
  creationTime: null,
  lastUpdateTime: null,
  tags: [],
} satisfies Job;

const mockCalledProcessInstance = {
  processInstanceKey: '987654321',
  processDefinitionId: 'called-process-def',
  processDefinitionKey: '888999000',
  processDefinitionName: 'Called Process',
  processDefinitionVersion: 1,
  state: 'ACTIVE',
  startDate: '2023-01-15T10:00:00.000Z',
  tenantId: '<default>',
  parentProcessInstanceKey: PROCESS_INSTANCE_ID,
  parentElementInstanceKey: '123456789',
  hasIncident: false,
  rootProcessInstanceKey: null,
  tags: [],
  processDefinitionVersionTag: null,
  endDate: null,
} satisfies ProcessInstance;

const mockProcessInstance = {
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionId: 'process-def-1',
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  processDefinitionName: 'Main Process',
  processDefinitionVersion: 1,
  state: 'ACTIVE',
  startDate: '2023-01-15T10:00:00.000Z',
  tenantId: '<default>',
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  hasIncident: false,
  rootProcessInstanceKey: null,
  tags: [],
  processDefinitionVersionTag: null,
  endDate: null,
} satisfies ProcessInstance;

const mockCalledDecisionInstance = {
  decisionEvaluationInstanceKey: '777888999',
  decisionEvaluationKey: '777888999',
  decisionDefinitionKey: '111222333444',
  decisionDefinitionName: 'My Decision',
  decisionDefinitionId: 'decision-1',
  decisionDefinitionVersion: 1,
  decisionDefinitionType: 'DECISION_TABLE',
  rootDecisionDefinitionKey: '111222333444',
  result: '"approved"',
  evaluationDate: '2023-01-15T10:00:00.000Z',
  evaluationFailure: null,
  state: 'EVALUATED',
  elementInstanceKey: '123456789',
  processInstanceKey: PROCESS_INSTANCE_ID,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  tenantId: '<default>',
  rootProcessInstanceKey: null,
} satisfies DecisionInstance;

const mockUserTask = {
  userTaskKey: '999888777',
  state: 'CREATED',
  processDefinitionVersion: 1,
  processDefinitionId: 'process-def-1',
  processName: null,
  processInstanceKey: PROCESS_INSTANCE_ID,
  rootProcessInstanceKey: null,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
  name: 'User Task',
  elementId: 'Task_1',
  elementInstanceKey: '123456789',
  tenantId: '<default>',
  assignee: null,
  candidateGroups: [],
  candidateUsers: [],
  dueDate: null,
  followUpDate: null,
  creationDate: '2023-01-15T10:00:00.000Z',
  completionDate: null,
  customHeaders: null,
  formKey: null,
  externalFormReference: null,
  tags: [],
  priority: 50,
} satisfies UserTask;

function getWrapper(initialSearchParams?: string) {
  const path = Paths.processInstanceDetails({
    processInstanceId: PROCESS_INSTANCE_ID,
  });
  const initialEntry = initialSearchParams
    ? `${path}?${initialSearchParams}`
    : path;

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    const router = createMemoryRouter(
      [
        {
          path: Paths.processInstance(undefined, true),
          children: [
            {
              path: Paths.processInstanceDetails({isRelative: true}),
              element: children,
            },
          ],
        },
      ],
      {
        initialEntries: [initialEntry],
      },
    );

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<DetailsTab />', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchJobs().withSuccess(searchResult([]));
    mockSearchProcessInstances().withSuccess(searchResult([]));
    mockSearchDecisionInstances().withSuccess(searchResult([]));
  });

  it('should show multi-instance message when multiple instances exist', async () => {
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance, mockElementInstance], 2),
    );

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1'),
    });

    expect(
      await screen.findByText(
        'To view the details, select a single element instance in the instance history.',
      ),
    ).toBeInTheDocument();
  });

  it('should render element instance details when a single instance is resolved', async () => {
    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('123456789')).toBeInTheDocument();
    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
  });

  it('should display execution duration', async () => {
    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Execution Duration')).toBeInTheDocument();
    expect(screen.getByText('5 minutes')).toBeInTheDocument();
  });

  it('should display job retries when available', async () => {
    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);
    mockSearchJobs().withSuccess(searchResult([mockJob]));

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Retries Left')).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('3');
  });

  it('should hide job retries when no job exists', async () => {
    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.queryByText('Retries Left')).not.toBeInTheDocument();
  });

  it('should display called process instance link for call activities', async () => {
    const callActivityInstance: ElementInstance = {
      ...mockElementInstance,
      type: 'CALL_ACTIVITY',
    };

    mockFetchProcessDefinitionXml().withSuccess(CALL_ACTIVITY_XML);
    mockFetchElementInstance('123456789').withSuccess(callActivityInstance);
    mockSearchProcessInstances().withSuccess(
      searchResult([mockCalledProcessInstance]),
    );

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText('Called Process Instance'),
    ).toBeInTheDocument();

    const link = await screen.findByRole('link', {
      name: /View Called Process instance 987654321/,
    });
    expect(link).toBeInTheDocument();
    expect(link).toHaveTextContent('Called Process - 987654321');
  });

  it('should display view all link when multiple called process instances exist', async () => {
    const callActivityInstance = {
      ...mockElementInstance,
      type: 'CALL_ACTIVITY',
    } satisfies ElementInstance;

    const secondCalledProcessInstance = {
      ...mockCalledProcessInstance,
      processInstanceKey: '111111111',
    } satisfies ProcessInstance;

    mockFetchProcessDefinitionXml().withSuccess(CALL_ACTIVITY_XML);
    mockFetchElementInstance('123456789').withSuccess(callActivityInstance);
    mockSearchProcessInstances().withSuccess(
      searchResult([mockCalledProcessInstance, secondCalledProcessInstance], 2),
    );

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText('Called Process Instance'),
    ).toBeInTheDocument();

    const link = await screen.findByRole('link', {
      name: /View all called process instances/,
    });
    expect(link).toBeInTheDocument();
    expect(link).toHaveTextContent('View all (2)');
    expect(link).toHaveAttribute(
      'href',
      `/processes?parentProcessInstanceKey=${PROCESS_INSTANCE_ID}&active=true&incidents=true&completed=true&canceled=true`,
    );
  });

  it('should display called decision instance link for business rule tasks', async () => {
    const businessRuleTaskInstance = {
      ...mockElementInstance,
      type: 'BUSINESS_RULE_TASK',
    } satisfies ElementInstance;

    mockFetchProcessDefinitionXml().withSuccess(BUSINESS_RULE_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess(businessRuleTaskInstance);
    mockSearchDecisionInstances().withSuccess(
      searchResult([mockCalledDecisionInstance]),
    );

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText('Called Decision Instance'),
    ).toBeInTheDocument();

    const link = await screen.findByRole('link', {
      name: /View My Decision instance 777888999/,
    });
    expect(link).toBeInTheDocument();
    expect(link).toHaveTextContent('My Decision - 777888999');
  });

  it('should display deprecation warning for job worker user tasks', async () => {
    const userTaskInstance = {
      ...mockElementInstance,
      type: 'USER_TASK',
    } satisfies ElementInstance;

    mockFetchProcessDefinitionXml().withSuccess(USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess(userTaskInstance);

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(
      await screen.findByText(/job worker implementation are deprecated/),
    ).toBeInTheDocument();
  });

  it('should handle element instance with no start date gracefully', async () => {
    const noStartDateInstance = {
      ...mockElementInstance,
      startDate: '',
      endDate: null,
    } satisfies ElementInstance;

    mockFetchElementInstance('123456789').withSuccess(noStartDateInstance);

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Element Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Execution Duration')).toBeInTheDocument();
    expect(screen.getByText('-')).toBeInTheDocument();
  });

  it('should display open tasklist link for camunda user tasks when tasklistUrl is configured', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      tasklistUrl: 'https://tasklist.example.com',
    });

    mockFetchProcessDefinitionXml().withSuccess(CAMUNDA_USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });
    mockSearchUserTasks().withSuccess(searchResult([mockUserTask]));

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    const link = await screen.findByRole('link', {
      name: 'Open Tasklist',
    });
    expect(link).toBeInTheDocument();
    await waitFor(() => {
      expect(link).toHaveAttribute(
        'href',
        'https://tasklist.example.com/999888777?filter=all-open',
      );
    });
  });

  it('should link to completed filter for completed user tasks', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      tasklistUrl: 'https://tasklist.example.com',
    });

    mockFetchProcessDefinitionXml().withSuccess(CAMUNDA_USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });
    mockSearchUserTasks().withSuccess(
      searchResult([
        {
          ...mockUserTask,
          state: 'COMPLETED',
          completionDate: '2023-01-15T10:05:00.000Z',
        },
      ]),
    );

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    const link = await screen.findByRole('link', {
      name: 'Open Tasklist',
    });
    await waitFor(() => {
      expect(link).toHaveAttribute(
        'href',
        'https://tasklist.example.com/999888777?filter=completed',
      );
    });
  });

  it('should not display open tasklist link for job worker user tasks', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      tasklistUrl: 'https://tasklist.example.com',
    });

    mockFetchProcessDefinitionXml().withSuccess(USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Element Instance Key')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('should not display open tasklist link when tasklistUrl is not configured', async () => {
    mockFetchProcessDefinitionXml().withSuccess(CAMUNDA_USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Element Instance Key')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('should not display open tasklist link for non-user-task elements', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      tasklistUrl: 'https://tasklist.example.com',
    });

    mockFetchElementInstance('123456789').withSuccess(mockElementInstance);

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    expect(await screen.findByText('Element Instance Key')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', {name: 'Open Tasklist'}),
    ).not.toBeInTheDocument();
  });

  it('should handle tasklistUrl with trailing slash', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      tasklistUrl: 'https://tasklist.example.com/',
    });

    mockFetchProcessDefinitionXml().withSuccess(CAMUNDA_USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });
    mockSearchUserTasks().withSuccess(searchResult([mockUserTask]));

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    const link = await screen.findByRole('link', {
      name: 'Open Tasklist',
    });
    await waitFor(() => {
      expect(link).toHaveAttribute(
        'href',
        'https://tasklist.example.com/999888777?filter=all-open',
      );
    });
  });

  it('should handle tasklistUrl with a subpath', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      tasklistUrl: 'https://tasklist.example.com/tasklist',
    });

    mockFetchProcessDefinitionXml().withSuccess(CAMUNDA_USER_TASK_XML);
    mockFetchElementInstance('123456789').withSuccess({
      ...mockElementInstance,
      type: 'USER_TASK',
    });
    mockSearchUserTasks().withSuccess(searchResult([mockUserTask]));

    render(<DetailsTab />, {
      wrapper: getWrapper('elementId=Task_1&elementInstanceKey=123456789'),
    });

    const link = await screen.findByRole('link', {
      name: 'Open Tasklist',
    });
    await waitFor(() => {
      expect(link).toHaveAttribute(
        'href',
        'https://tasklist.example.com/tasklist/999888777?filter=all-open',
      );
    });
  });
});
