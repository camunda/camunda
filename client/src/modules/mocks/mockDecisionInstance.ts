/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockDecisionInstance = {
  decisionDefinitionId: '111',
  decisionId: 'invoiceClassification',
  state: 'completed',
  name: 'Invoice Classification',
  version: '1',
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceId: '666',
} as const;

export {mockDecisionInstance};
