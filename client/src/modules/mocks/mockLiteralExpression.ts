/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockLiteralExpression = {
  decisionDefinitionId: '111',
  decisionId: 'calc-key-figures',
  state: 'completed',
  name: 'Calculate Credit History Key Figures',
  version: '1',
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceId: '42',
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
      name: 'paragraph',
      value: '"sbl §201"',
    },
  ],
} as const;

export {mockLiteralExpression};
