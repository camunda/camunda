/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {openFile} from '@/utils/openFile';
import type {Route} from '@playwright/test';
import type {
  GetDecisionInstanceResponseBody,
  QueryDecisionInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

type DecisionInstanceEntityState = 'EVALUATED' | 'FAILED';
type DecisionInstanceDto = {
  id: string;
  state: DecisionInstanceEntityState;
  decisionType: 'DECISION_TABLE' | 'LITERAL_EXPRESSION';
  decisionDefinitionId: string;
  decisionId: string;
  decisionName: string;
  decisionVersion: number;
  tenantId: string;
  evaluationDate: string;
  errorMessage: string | null;
  processInstanceId: string | null;
  result: string | null;
  evaluatedInputs: Array<{
    id: string;
    name: string;
    value: string | null;
  }>;
  evaluatedOutputs: Array<{
    id: string;
    ruleIndex: number;
    ruleId: string;
    name: string;
    value: string | null;
  }>;
};

const mockEvaluatedDecisionInstance: DecisionInstanceDto = {
  id: '2251799813830820-1',
  tenantId: '<default>',
  state: 'EVALUATED',
  decisionType: 'DECISION_TABLE',
  decisionDefinitionId: '2251799813687886',
  decisionId: 'invoiceClassification',
  decisionName: 'Invoice Classification',
  decisionVersion: 2,
  evaluationDate: '2023-08-14T05:47:07.123+0000',
  errorMessage: null,
  processInstanceId: '2251799813830813',
  result: '"budget"',
  evaluatedInputs: [
    {
      id: 'clause1',
      name: 'Invoice Amount',
      value: '1000',
    },
    {
      id: 'InputClause_15qmk0v',
      name: 'Invoice Category',
      value: '"Misc"',
    },
  ],
  evaluatedOutputs: [
    {
      id: 'clause3',
      name: 'Classification',
      value: '"budget"',
      ruleId: 'DecisionRule_1ak4z14',
      ruleIndex: 2,
    },
  ],
};

const mockEvaluatedDecisionInstancesSearch: QueryDecisionInstancesResponseBody = {
  items: [
    mapDecisionInstanceToV2({
      ...mockEvaluatedDecisionInstance,
      decisionId: 'invoiceAssignApprover',
      id: '2251799813830820-3',
    }),
    mapDecisionInstanceToV2({
      ...mockEvaluatedDecisionInstance,
      decisionId: 'invoiceClassification',
      id: '2251799813830820-1',
    }),
    mapDecisionInstanceToV2({
      ...mockEvaluatedDecisionInstance,
      decisionId: 'amountToString',
      id: '2251799813830820-2',
    }),
  ],
  page: {totalItems: 3},
};

const mockEvaluatedXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluated.dmn',
);

const mockEvaluatedDecisionInstanceWithoutPanels: DecisionInstanceDto = {
  id: '2251799813830820-2',
  tenantId: '<default>',
  state: 'EVALUATED',
  decisionType: 'LITERAL_EXPRESSION',
  decisionDefinitionId: '2251799813687887',
  decisionId: 'amountToString',
  decisionName: 'Convert amount to string',
  decisionVersion: 1,
  evaluationDate: '2023-08-14T05:47:07.123+0000',
  errorMessage: null,
  processInstanceId: '2251799813830813',
  result: '"$1000"',
  evaluatedInputs: [],
  evaluatedOutputs: [],
};

const mockEvaluatedDecisionInstancesSearchWithoutPanels: QueryDecisionInstancesResponseBody = {
  items: [
    mapDecisionInstanceToV2({
      ...mockEvaluatedDecisionInstanceWithoutPanels,
      decisionId: 'invoiceAssignApprover',
      id: '2251799813830820-3',
    }),
    mapDecisionInstanceToV2({
      ...mockEvaluatedDecisionInstanceWithoutPanels,
      decisionId: 'invoiceClassification',
      id: '2251799813830820-1',
    }),
    mapDecisionInstanceToV2({
      ...mockEvaluatedDecisionInstanceWithoutPanels,
      decisionId: 'amountToString',
      id: '2251799813830820-2',
    }),
  ],
  page: {totalItems: 3},
};

const mockEvaluatedXmlWithoutPanels = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluatedWithoutPanels.dmn',
);

const mockFailedDecisionInstance: DecisionInstanceDto = {
  id: '6755399441062312-1',
  tenantId: '<default>',
  state: 'FAILED',
  decisionType: 'DECISION_TABLE',
  decisionDefinitionId: '2251799813687886',
  decisionId: 'invoiceClassification',
  decisionName: 'Invoice Classification',
  decisionVersion: 2,
  evaluationDate: '2023-08-14T05:47:06.793+0000',
  errorMessage:
    "Expected to evaluate decision 'invoiceAssignApprover', but failed to evaluate expression 'amount': no variable found for name 'amount'",
  processInstanceId: '6755399441062307',
  result: 'null',
  evaluatedInputs: [],
  evaluatedOutputs: [],
};

const mockFailedDecisionInstancesSearch: QueryDecisionInstancesResponseBody = {
  items: [mapDecisionInstanceToV2(mockFailedDecisionInstance)],
  page: {totalItems: 1},
};

const mockFailedXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationFailed.dmn',
);
const mockEvaluatedLargeXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluatedLarge.dmn',
);

// TODO: Apply the transformations of this function to the existing mock data when
// the GET /api/decision-instances/<key> endpoint related code is removed. This should happen
// with https://github.com/camunda/camunda/issues/28392
function mapDecisionInstanceToV2(
  instance: DecisionInstanceDto,
): GetDecisionInstanceResponseBody {
  return {
    decisionEvaluationInstanceKey: instance.id,
    decisionEvaluationKey: '29283472932831',
    tenantId: instance.tenantId,
    decisionDefinitionKey: instance.decisionDefinitionId,
    decisionDefinitionId: instance.decisionId,
    state: instance.state,
    decisionDefinitionName: instance.decisionName,
    decisionDefinitionVersion: instance.decisionVersion,
    evaluationDate: instance.evaluationDate,
    processInstanceKey: instance.processInstanceId ?? '6755399441062307',
    processDefinitionKey: instance.processInstanceId ?? '6755399441062307',
    elementInstanceKey: '2347238947239',
    evaluationFailure: instance.errorMessage ?? '',
    evaluatedInputs: instance.evaluatedInputs.map((input) => ({
      inputId: input.id,
      inputName: input.name,
      inputValue: input.value ?? '',
    })),
    matchedRules: instance.evaluatedOutputs.map((out) => ({
      ruleId: out.ruleId,
      ruleIndex: out.ruleIndex,
      evaluatedOutputs: [
        {outputId: out.id, outputName: out.name, outputValue: out.value ?? ''},
      ],
    })),
    decisionDefinitionType: instance.decisionType,
    result: instance.result ?? '',
  };
}

function mockResponses({
  decisionInstanceDetail,
  decisionInstancesSearch,
  xml,
}: {
  decisionInstanceDetail?: DecisionInstanceDto;
  decisionInstancesSearch?: QueryDecisionInstancesResponseBody;
  xml?: string;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          displayName: 'demo',
          canLogout: true,
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/decision-instances/')) {
      return route.fulfill({
        status: decisionInstanceDetail === undefined ? 400 : 200,
        body: JSON.stringify(decisionInstanceDetail),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('v2/decision-instances/search')) {
      return route.fulfill({
        status: decisionInstancesSearch === undefined ? 400 : 200,
        body: JSON.stringify(decisionInstancesSearch),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/decision-instances/')) {
      if (!decisionInstanceDetail) {
        return route.fulfill({status: 400});
      }

      const decisionInstanceV2 = mapDecisionInstanceToV2(
        decisionInstanceDetail,
      );
      return route.fulfill({
        status: 200,
        body: JSON.stringify(decisionInstanceV2),
        headers: {'content-type': 'application/json'},
      });
    }

    if (
      route
        .request()
        .url()
        .match(/\/v2\/decision-definitions\/\d+\/xml/)
    ) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: xml,
        headers: {
          'content-type': 'application/text',
        },
      });
    }

    route.continue();
  };
}

export {
  mockEvaluatedDecisionInstance,
  mockEvaluatedDecisionInstancesSearch,
  mockEvaluatedXml,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDecisionInstancesSearchWithoutPanels,
  mockEvaluatedXmlWithoutPanels,
  mockFailedDecisionInstance,
  mockFailedDecisionInstancesSearch,
  mockFailedXml,
  mockEvaluatedLargeXml,
  mockResponses,
};
