/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {openFile} from '@/utils/openFile';
import {Route} from '@playwright/test';

interface DecisionInstanceEntity {
  id: string;
  decisionName: string;
  decisionVersion: number;
  tenantId: string;
  evaluationDate: string;
  processInstanceId: string | null;
  state: DecisionInstanceEntityState;
  sortValues: [string, string];
}
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
type DrdDataDto = {
  [decisionId: string]: [
    {
      decisionInstanceId: DecisionInstanceEntity['id'];
      state: DecisionInstanceEntityState;
    },
  ];
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

const mockEvaluatedDrdData: DrdDataDto = {
  invoiceAssignApprover: [
    {
      decisionInstanceId: '2251799813830820-3',
      state: 'EVALUATED',
    },
  ],
  invoiceClassification: [
    {
      decisionInstanceId: '2251799813830820-1',
      state: 'EVALUATED',
    },
  ],
  amountToString: [
    {
      decisionInstanceId: '2251799813830820-2',
      state: 'EVALUATED',
    },
  ],
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

const mockEvaluatedDrdDataWithoutPanels: DrdDataDto = {
  invoiceAssignApprover: [
    {
      decisionInstanceId: '2251799813830820-3',
      state: 'EVALUATED',
    },
  ],
  invoiceClassification: [
    {
      decisionInstanceId: '2251799813830820-1',
      state: 'EVALUATED',
    },
  ],
  amountToString: [
    {
      decisionInstanceId: '2251799813830820-2',
      state: 'EVALUATED',
    },
  ],
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

const mockFailedDrdData: DrdDataDto = {
  invoiceClassification: [
    {
      decisionInstanceId: '6755399441062312-1',
      state: 'FAILED',
    },
  ],
};

const mockFailedXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationFailed.dmn',
);
const mockEvaluatedLargeXml = openFile(
  './e2e-playwright/mocks/resources/invoiceClassificationEvaluatedLarge.dmn',
);

function mockResponses({
  decisionInstanceDetail,
  drdData,
  xml,
}: {
  decisionInstanceDetail?: DecisionInstanceDto;
  drdData?: DrdDataDto;
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

    if (route.request().url().includes('drd-data')) {
      return route.fulfill({
        status: drdData === undefined ? 400 : 200,
        body: JSON.stringify(drdData),
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
  mockEvaluatedDrdData,
  mockEvaluatedXml,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDrdDataWithoutPanels,
  mockEvaluatedXmlWithoutPanels,
  mockFailedDecisionInstance,
  mockFailedDrdData,
  mockFailedXml,
  mockEvaluatedLargeXml,
  mockResponses,
};
