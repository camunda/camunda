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

const mockEvaluatedDecisionInstance: GetDecisionInstanceResponseBody = {
  decisionEvaluationInstanceKey: '2251799813830820-1',
  decisionEvaluationKey: '2251799813830820',
  rootDecisionDefinitionKey: '2251799813687886',
  tenantId: '<default>',
  decisionDefinitionKey: '2251799813687886',
  decisionDefinitionId: 'invoiceClassification',
  state: 'EVALUATED',
  decisionDefinitionName: 'Invoice Classification',
  decisionDefinitionVersion: 2,
  evaluationDate: '2023-08-14T05:47:07.123+0000',
  processInstanceKey: '2251799813830813',
  processDefinitionKey: '2251799813830813',
  elementInstanceKey: '2347238947239',
  evaluationFailure: '',
  evaluatedInputs: [
    {
      inputId: 'clause1',
      inputName: 'Invoice Amount',
      inputValue: '1000',
    },
    {
      inputId: 'InputClause_15qmk0v',
      inputName: 'Invoice Category',
      inputValue: '"Misc"',
    },
  ],
  matchedRules: [
    {
      ruleId: 'DecisionRule_1ak4z14',
      ruleIndex: 2,
      evaluatedOutputs: [
        {
          outputId: 'clause3',
          outputName: 'Classification',
          outputValue: '"budget"',
        },
      ],
    },
  ],
  decisionDefinitionType: 'DECISION_TABLE',
  result: '"budget"',
};

const mockEvaluatedDecisionInstancesSearch: QueryDecisionInstancesResponseBody =
  {
    items: [
      {
        ...mockEvaluatedDecisionInstance,
        decisionDefinitionId: 'invoiceAssignApprover',
        decisionEvaluationInstanceKey: '2251799813830820-3',
      },
      {
        ...mockEvaluatedDecisionInstance,
        decisionDefinitionId: 'invoiceClassification',
        decisionEvaluationInstanceKey: '2251799813830820-1',
      },
      {
        ...mockEvaluatedDecisionInstance,
        decisionDefinitionId: 'amountToString',
        decisionEvaluationInstanceKey: '2251799813830820-2',
      },
    ],
    page: {totalItems: 3},
  };

const mockEvaluatedXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluated.dmn',
);

const mockEvaluatedDecisionInstanceWithoutPanels: GetDecisionInstanceResponseBody =
  {
    decisionEvaluationInstanceKey: '2251799813830820-2',
    decisionEvaluationKey: '2251799813830820',
    tenantId: '<default>',
    decisionDefinitionKey: '2251799813687887',
    rootDecisionDefinitionKey: '2251799813687887',
    decisionDefinitionId: 'amountToString',
    state: 'EVALUATED',
    decisionDefinitionName: 'Convert amount to string',
    decisionDefinitionVersion: 1,
    evaluationDate: '2023-08-14T05:47:07.123+0000',
    processInstanceKey: '2251799813830813',
    processDefinitionKey: '2251799813830813',
    elementInstanceKey: '2347238947239',
    evaluationFailure: '',
    evaluatedInputs: [],
    matchedRules: [],
    decisionDefinitionType: 'LITERAL_EXPRESSION',
    result: '"$1000"',
  };

const mockEvaluatedDecisionInstancesSearchWithoutPanels: QueryDecisionInstancesResponseBody =
  {
    items: [
      {
        ...mockEvaluatedDecisionInstanceWithoutPanels,
        decisionDefinitionId: 'invoiceAssignApprover',
        decisionEvaluationInstanceKey: '2251799813830820-3',
      },
      {
        ...mockEvaluatedDecisionInstanceWithoutPanels,
        decisionDefinitionId: 'invoiceClassification',
        decisionEvaluationInstanceKey: '2251799813830820-1',
      },
      {
        ...mockEvaluatedDecisionInstanceWithoutPanels,
        decisionDefinitionId: 'amountToString',
        decisionEvaluationInstanceKey: '2251799813830820-2',
      },
    ],
    page: {totalItems: 3},
  };

const mockEvaluatedXmlWithoutPanels = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluatedWithoutPanels.dmn',
);

const mockFailedDecisionInstance: GetDecisionInstanceResponseBody = {
  decisionEvaluationInstanceKey: '6755399441062312-1',
  decisionEvaluationKey: '6755399441062312',
  tenantId: '<default>',
  decisionDefinitionKey: '2251799813687886',
  rootDecisionDefinitionKey: '2251799813687886',
  decisionDefinitionId: 'invoiceClassification',
  state: 'FAILED',
  decisionDefinitionName: 'Invoice Classification',
  decisionDefinitionVersion: 2,
  evaluationDate: '2023-08-14T05:47:06.793+0000',
  processInstanceKey: '6755399441062307',
  processDefinitionKey: '6755399441062307',
  elementInstanceKey: '2347238947239',
  evaluationFailure:
    "Expected to evaluate decision 'invoiceAssignApprover', but failed to evaluate expression 'amount': no variable found for name 'amount'",
  evaluatedInputs: [],
  matchedRules: [],
  decisionDefinitionType: 'DECISION_TABLE',
  result: 'null',
};

const mockFailedDecisionInstancesSearch: QueryDecisionInstancesResponseBody = {
  items: [mockFailedDecisionInstance],
  page: {totalItems: 1},
};

const mockFailedXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationFailed.dmn',
);
const mockEvaluatedLargeXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluatedLarge.dmn',
);

function mockResponses({
  decisionInstanceDetail,
  decisionInstancesSearch,
  xml,
}: {
  decisionInstanceDetail?: GetDecisionInstanceResponseBody;
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
      return route.fulfill({
        status: decisionInstanceDetail === undefined ? 400 : 200,
        body: JSON.stringify(decisionInstanceDetail),
        headers: {
          'content-type': 'application/json',
        },
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
