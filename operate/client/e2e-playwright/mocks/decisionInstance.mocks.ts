/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Route} from '@playwright/test';
import {DecisionInstanceDto} from 'modules/api/decisionInstances/fetchDecisionInstance';
import {DrdDataDto} from 'modules/api/decisionInstances/fetchDrdData';
import {open} from './resources';

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

const mockEvaluatedXml = open('invoiceClassificationEvaluated.dmn');

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

const mockEvaluatedXmlWithoutPanels = open(
  'invoiceClassificationEvaluatedWithoutPanels.dmn',
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

const mockFailedXml = open('invoiceClassificationFailed.dmn');

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
    if (route.request().url().includes('/api/authentications/user')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          permissions: ['read', 'write'],
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

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: JSON.stringify(xml),
        headers: {
          'content-type': 'application/json',
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
  mockResponses,
};
