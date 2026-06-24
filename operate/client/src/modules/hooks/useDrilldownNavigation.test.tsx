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
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {notificationsStore} from 'modules/stores/notifications';
import {Paths} from 'modules/Routes';
import {createProcessInstance, searchResult} from 'modules/testUtils';
import type {QueryDecisionInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

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

function createDecisionSearchResult(
  items: QueryDecisionInstancesResponseBody['items'],
  totalItems = items.length,
) {
  return searchResult(items, totalItems);
}

describe('useDrillDownNavigation', () => {
  it('should navigate directly to the called process instance when there is exactly one', async () => {
    const calledInstance = createProcessInstance({
      processInstanceKey: 'called-200',
    });

    mockSearchProcessInstances().withSuccess(searchResult([calledInstance], 1));

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown(CALL_ACTIVITY_ID, 'bpmn:CallActivity');
    });

    expect(mockNavigate).toHaveBeenCalledWith(
      Paths.processInstance('called-200'),
    );
  });

  it('should not navigate when there are multiple called instances', async () => {
    const calledInstances = [
      createProcessInstance({processInstanceKey: 'called-200'}),
      createProcessInstance({processInstanceKey: 'called-201'}),
    ];

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

  it('should show error toast when process API call fails', async () => {
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
          elementInstanceKey: 'el-inst-1',
          rootDecisionDefinitionKey: 'def-1',
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

  it('should not navigate when there are multiple decision instances', async () => {
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
            elementInstanceKey: 'el-inst-1',
            rootDecisionDefinitionKey: 'def-1',
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
