/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, act} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {useDrillDownNavigation} from './useDrilldownNavigation';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {notificationsStore} from 'modules/stores/notifications';
import {Paths} from 'modules/Routes';
import {createProcessInstance, searchResult} from 'modules/testUtils';
import type {
  ElementInstance,
  QueryDecisionInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual =
    await vi.importActual<typeof import('react-router-dom')>(
      'react-router-dom',
    );
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const PROCESS_INSTANCE_KEY = '2251799813685249';
const CALL_ACTIVITY_ID = 'confirmDelivery';
const BUSINESS_RULE_TASK_ID = 'evaluateRisk';
const CALL_ACTIVITY_ELEMENT_INSTANCE_KEY = 'element-instance-100';
const BUSINESS_RULE_TASK_ELEMENT_INSTANCE_KEY = 'element-instance-200';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {queries: {retry: false}},
  });

  return ({children}: {children: React.ReactNode}) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

function createElementInstance(
  options: Partial<ElementInstance> = {},
): ElementInstance {
  return {
    processDefinitionId: 'process',
    startDate: '2024-01-01T00:00:00.000+0000',
    endDate: null,
    elementId: CALL_ACTIVITY_ID,
    elementName: null,
    type: 'CALL_ACTIVITY',
    state: 'ACTIVE',
    hasIncident: false,
    tenantId: '<default>',
    elementInstanceKey: CALL_ACTIVITY_ELEMENT_INSTANCE_KEY,
    processInstanceKey: PROCESS_INSTANCE_KEY,
    rootProcessInstanceKey: null,
    processDefinitionKey: 'process-def-1',
    incidentKey: null,
    ...options,
  };
}

function createDecisionSearchResult(
  items: QueryDecisionInstancesResponseBody['items'],
  totalItems = items.length,
) {
  return searchResult(items, totalItems);
}

function mockResolvedCallActivityElement() {
  mockSearchElementInstances().withSuccess(
    searchResult([createElementInstance()], 1),
  );
}

function mockResolvedBusinessRuleTaskElement() {
  mockSearchElementInstances().withSuccess(
    searchResult(
      [
        createElementInstance({
          elementId: BUSINESS_RULE_TASK_ID,
          type: 'BUSINESS_RULE_TASK',
          elementInstanceKey: BUSINESS_RULE_TASK_ELEMENT_INSTANCE_KEY,
        }),
      ],
      1,
    ),
  );
}

describe('useDrillDownNavigation', () => {
  it('should navigate directly to the called process instance when there is exactly one', async () => {
    const calledInstance = createProcessInstance({
      processInstanceKey: 'called-200',
    });

    mockResolvedCallActivityElement();
    mockSearchProcessInstances().withSuccess(searchResult([calledInstance], 1));

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(CALL_ACTIVITY_ID, 'bpmn:CallActivity');
    });

    expect(mockNavigate).toHaveBeenCalledWith(
      Paths.processInstanceDetails({processInstanceId: 'called-200'}),
    );
  });

  it('should not navigate when the clicked element has multiple called instances', async () => {
    const calledInstances = [
      createProcessInstance({processInstanceKey: 'called-200'}),
      createProcessInstance({processInstanceKey: 'called-201'}),
    ];

    mockResolvedCallActivityElement();
    mockSearchProcessInstances().withSuccess(searchResult(calledInstances, 2));

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(CALL_ACTIVITY_ID, 'bpmn:CallActivity');
    });

    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('should not navigate when the clicked element itself is ambiguous (e.g. multi-instance)', async () => {
    mockSearchElementInstances().withSuccess(
      searchResult(
        [
          createElementInstance({elementInstanceKey: 'element-instance-a'}),
          createElementInstance({elementInstanceKey: 'element-instance-b'}),
        ],
        2,
      ),
    );
    // No handler registered for the process-instances search: if the hook
    // incorrectly fell through to it, the request would fail and surface as
    // an error toast instead of a silent no-navigate.

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(CALL_ACTIVITY_ID, 'bpmn:CallActivity');
    });

    expect(mockNavigate).not.toHaveBeenCalled();
    expect(notificationsStore.displayNotification).not.toHaveBeenCalled();
  });

  it('should scope the called-instance lookup to the clicked call activity, not the whole process instance', async () => {
    mockResolvedCallActivityElement();
    const requestBodyResolverFn = vi.fn();
    mockSearchProcessInstances().withSuccess(
      searchResult(
        [createProcessInstance({processInstanceKey: 'called-200'})],
        1,
      ),
      {requestBodyResolverFn},
    );

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(CALL_ACTIVITY_ID, 'bpmn:CallActivity');
    });

    expect(requestBodyResolverFn).toHaveBeenCalledWith(
      expect.objectContaining({
        filter: {parentElementInstanceKey: CALL_ACTIVITY_ELEMENT_INSTANCE_KEY},
      }),
    );
  });

  it('should show error toast when process API call fails', async () => {
    mockResolvedCallActivityElement();
    mockSearchProcessInstances().withServerError();

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(CALL_ACTIVITY_ID, 'bpmn:CallActivity');
    });

    expect(mockNavigate).not.toHaveBeenCalled();
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to resolve called instances',
      isDismissable: true,
    });
  });

  it('should navigate to the decision instance when there is exactly one', async () => {
    mockResolvedBusinessRuleTaskElement();
    mockSearchDecisionInstances().withSuccess(
      createDecisionSearchResult([
        {
          decisionEvaluationInstanceKey: 'dec-100',
          decisionEvaluationKey: 'dec-eval-100',
          state: 'EVALUATED',
          evaluationDate: '2024-01-01T00:00:00.000+0000',
          evaluationFailure: null,
          decisionDefinitionId: 'risk-assessment',
          decisionDefinitionName: 'Risk Assessment',
          decisionDefinitionVersion: 1,
          decisionDefinitionType: 'DECISION_TABLE',
          decisionDefinitionKey: 'def-1',
          result: '',
          tenantId: '<default>',
          processDefinitionKey: 'proc-def-1',
          processInstanceKey: PROCESS_INSTANCE_KEY,
          rootProcessInstanceKey: null,
          elementInstanceKey: BUSINESS_RULE_TASK_ELEMENT_INSTANCE_KEY,
          rootDecisionDefinitionKey: 'def-1',
          businessId: null,
        },
      ]),
    );

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(
        BUSINESS_RULE_TASK_ID,
        'bpmn:BusinessRuleTask',
      );
    });

    expect(mockNavigate).toHaveBeenCalledWith(
      Paths.decisionInstance('dec-100'),
    );
  });

  it('should scope the called-decision lookup to the clicked business rule task, not the whole process instance', async () => {
    mockResolvedBusinessRuleTaskElement();
    const requestBodyResolverFn = vi.fn();
    mockSearchDecisionInstances().withSuccess(
      createDecisionSearchResult([
        {
          decisionEvaluationInstanceKey: 'dec-100',
          decisionEvaluationKey: 'dec-eval-100',
          state: 'EVALUATED',
          evaluationDate: '2024-01-01T00:00:00.000+0000',
          evaluationFailure: null,
          decisionDefinitionId: 'risk-assessment',
          decisionDefinitionName: 'Risk Assessment',
          decisionDefinitionVersion: 1,
          decisionDefinitionType: 'DECISION_TABLE',
          decisionDefinitionKey: 'def-1',
          result: '',
          tenantId: '<default>',
          processDefinitionKey: 'proc-def-1',
          processInstanceKey: PROCESS_INSTANCE_KEY,
          rootProcessInstanceKey: null,
          elementInstanceKey: BUSINESS_RULE_TASK_ELEMENT_INSTANCE_KEY,
          rootDecisionDefinitionKey: 'def-1',
          businessId: null,
        },
      ]),
      {requestBodyResolverFn},
    );

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(
        BUSINESS_RULE_TASK_ID,
        'bpmn:BusinessRuleTask',
      );
    });

    expect(requestBodyResolverFn).toHaveBeenCalledWith(
      expect.objectContaining({
        filter: {elementInstanceKey: BUSINESS_RULE_TASK_ELEMENT_INSTANCE_KEY},
      }),
    );
  });

  it('should not navigate when there are multiple decision instances', async () => {
    mockResolvedBusinessRuleTaskElement();
    mockSearchDecisionInstances().withSuccess(
      createDecisionSearchResult(
        [
          {
            decisionEvaluationInstanceKey: 'dec-100',
            decisionEvaluationKey: 'dec-eval-100',
            state: 'EVALUATED',
            evaluationDate: '2024-01-01T00:00:00.000+0000',
            evaluationFailure: null,
            decisionDefinitionId: 'risk-assessment',
            decisionDefinitionName: 'Risk Assessment',
            decisionDefinitionVersion: 1,
            decisionDefinitionType: 'DECISION_TABLE',
            decisionDefinitionKey: 'def-1',
            result: '',
            tenantId: '<default>',
            processDefinitionKey: 'proc-def-1',
            processInstanceKey: PROCESS_INSTANCE_KEY,
            rootProcessInstanceKey: null,
            elementInstanceKey: BUSINESS_RULE_TASK_ELEMENT_INSTANCE_KEY,
            rootDecisionDefinitionKey: 'def-1',
            businessId: null,
          },
        ],
        2,
      ),
    );

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(
        BUSINESS_RULE_TASK_ID,
        'bpmn:BusinessRuleTask',
      );
    });

    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('should show error toast when decision API call fails', async () => {
    mockResolvedBusinessRuleTaskElement();
    mockSearchDecisionInstances().withServerError();

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(
        BUSINESS_RULE_TASK_ID,
        'bpmn:BusinessRuleTask',
      );
    });

    expect(mockNavigate).not.toHaveBeenCalled();
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to resolve called decision instances',
      isDismissable: true,
    });
  });
});
