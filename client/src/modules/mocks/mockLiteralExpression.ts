/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DecisionInstanceType} from 'modules/stores/decisionInstanceDetails';

const mockLiteralExpression: DecisionInstanceType = {
  id: '247986278462738-1',
  decisionDefinitionId: '111',
  decisionId: 'calc-key-figures',
  state: 'EVALUATED',
  decisionName: 'Calculate Credit History Key Figures',
  decisionVersion: 1,
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceId: '42',
  errorMessage: null,
  evaluatedInputs: [
    {
      id: '0',
      name: 'Age',
      value: '21',
    },
  ],
  evaluatedOutputs: [
    {
      id: '0',
      ruleId: 'row-49839158-2',
      ruleIndex: 1,
      name: 'paragraph',
      value: '"sbl §201"',
    },
  ],
  result: null,
} as const;

export {mockLiteralExpression};
