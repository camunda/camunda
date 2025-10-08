/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {renderHook, waitFor} from 'modules/testing-library';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {
  assignApproverGroup,
  invoiceClassification,
  literalExpression,
} from 'modules/mocks/mockDecisionInstanceV2';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {useDrdData} from './useDrdData';

const wrapper = ({children}: {children: React.ReactNode}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

describe('useDrdData', () => {
  it('should map decision instances search results to drd data', async () => {
    mockSearchDecisionInstances().withSuccess({
      items: [invoiceClassification, assignApproverGroup],
      page: {totalItems: 2},
    });

    const {result} = renderHook(() => useDrdData('29283472932831'), {wrapper});

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({
      [assignApproverGroup.decisionDefinitionId]: {
        decisionDefinitionId: assignApproverGroup.decisionDefinitionId,
        decisionEvaluationInstanceKey:
          assignApproverGroup.decisionEvaluationInstanceKey,
        state: assignApproverGroup.state,
      },
      [invoiceClassification.decisionDefinitionId]: {
        decisionDefinitionId: invoiceClassification.decisionDefinitionId,
        decisionEvaluationInstanceKey:
          invoiceClassification.decisionEvaluationInstanceKey,
        state: invoiceClassification.state,
      },
    });
  });

  it('should use the last result item as the value for a decision instance id', async () => {
    mockSearchDecisionInstances().withSuccess({
      items: [
        invoiceClassification,
        {...invoiceClassification, state: 'FAILED'},
      ],
      page: {totalItems: 2},
    });

    const {result} = renderHook(() => useDrdData('29283472932831'), {wrapper});

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({
      [invoiceClassification.decisionDefinitionId]: {
        decisionDefinitionId: invoiceClassification.decisionDefinitionId,
        decisionEvaluationInstanceKey:
          invoiceClassification.decisionEvaluationInstanceKey,
        state: 'FAILED',
      },
    });
  });

  it('should load remaining results items if more are available', async () => {
    mockSearchDecisionInstances().withSuccess({
      items: [invoiceClassification],
      page: {totalItems: 3},
    });
    mockSearchDecisionInstances().withSuccess({
      items: [assignApproverGroup, literalExpression],
      page: {totalItems: 3},
    });

    const {result} = renderHook(() => useDrdData('29283472932831'), {wrapper});

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({
      [assignApproverGroup.decisionDefinitionId]: {
        decisionDefinitionId: assignApproverGroup.decisionDefinitionId,
        decisionEvaluationInstanceKey:
          assignApproverGroup.decisionEvaluationInstanceKey,
        state: assignApproverGroup.state,
      },
      [invoiceClassification.decisionDefinitionId]: {
        decisionDefinitionId: invoiceClassification.decisionDefinitionId,
        decisionEvaluationInstanceKey:
          invoiceClassification.decisionEvaluationInstanceKey,
        state: invoiceClassification.state,
      },
      [literalExpression.decisionDefinitionId]: {
        decisionDefinitionId: literalExpression.decisionDefinitionId,
        decisionEvaluationInstanceKey:
          literalExpression.decisionEvaluationInstanceKey,
        state: literalExpression.state,
      },
    });
  });
});
