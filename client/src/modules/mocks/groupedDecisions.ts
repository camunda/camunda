/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const groupedDecisions = [
  {
    decisionId: 'invoice-assign-approver',
    name: 'Assign Approver Group',
    decisions: [
      {
        id: '0',
        name: 'Assign Approver Group',
        version: 1,
        decisionId: 'invoice-assign-approver',
      },
      {
        id: '0',
        name: 'Assign Approver Group',
        version: 2,
        decisionId: 'invoice-assign-approver',
      },
    ],
  },
  {
    decisionId: 'calc-key-figures',
    name: 'Calculate Credit History Key Figures',
    decisions: [
      {
        id: '2',
        name: 'Calculate Credit History Key Figures',
        version: 1,
        decisionId: 'calc-key-figures',
      },
    ],
  },
  {
    decisionId: 'invoiceClassification',
    name: 'Invoice Classification',
    decisions: [
      {
        id: '1',
        name: 'Invoice Classification',
        version: 1,
        decisionId: 'invoiceClassification',
      },
    ],
  },
];

export {groupedDecisions};
