/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const invoiceClassification = {
  decisionDefinitionId: '111',
  decisionId: 'invoiceClassification',
  state: 'COMPLETED',
  name: 'Invoice Classification',
  version: '1',
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceId: '666',
  inputs: [
    {
      id: '0',
      name: 'Age',
      value: '16',
    },
    {
      id: '1',
      name: 'Stateless Person',
      value: 'false',
    },
    {
      id: '2',
      name: 'Parent is Norwegian',
      value: '"missing data"',
    },
    {
      id: '3',
      name: 'Previously Norwegian',
      value: 'true',
    },
  ],
  outputs: [
    {
      id: '0',
      rule: 5,
      name: 'Age requirements satisfied',
      value: '"missing data"',
    },
    {
      id: '1',
      rule: 6,
      name: 'paragraph',
      value: '"sbl §17"',
    },
  ],
} as const;

const assignApproverGroup = {
  decisionDefinitionId: '111',
  decisionId: 'invoice-assign-approver',
  state: 'FAILED',
  name: 'Assign Approver Group',
  version: '1',
  evaluationDate: '2022-01-20T13:26:52.531+0000',
  processInstanceId: '777',
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
      rule: 1,
      name: 'paragraph',
      value: '"sbl §382"',
    },
  ],
} as const;

export {invoiceClassification, assignApproverGroup};
