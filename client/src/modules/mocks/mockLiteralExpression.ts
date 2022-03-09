/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DecisionInstanceType} from 'modules/stores/decisionInstance';

const mockLiteralExpression: DecisionInstanceType = {
  decisionDefinitionId: '111',
  decisionId: 'calc-key-figures',
  state: 'COMPLETED',
  decisionName: 'Calculate Credit History Key Figures',
  decisionVersion: '1',
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceId: '42',
  errorMessage: null,
  inputs: [
    {
      id: '0',
      name: 'Age',
      value: '21',
    },
  ],
  outputs: [
    {
      id: '0',
      rule: 3,
      ruleIndex: 1,
      name: 'paragraph',
      value: '"sbl §201"',
    },
  ],
  result: null,
} as const;

export {mockLiteralExpression};
