/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const groupedDecisions = [
  {
    decisionId: 'invoice-assign-approver',
    name: 'Assign Approver Group',
    decisions: [
      {
        id: '0',
        version: 2,
        decisionId: 'invoice-assign-approver',
      },
      {
        id: '0',
        version: 1,
        decisionId: 'invoice-assign-approver',
      },
    ],
  },
  {
    decisionId: 'invoiceClassification',
    name: null,
    decisions: [
      {
        id: '1',
        version: 1,
        decisionId: 'invoiceClassification',
      },
    ],
  },
  {
    decisionId: 'calc-key-figures',
    name: 'Calculate Credit History Key Figures',
    decisions: [
      {
        id: '2',
        version: 1,
        decisionId: 'calc-key-figures',
      },
    ],
  },
] as const;

export {groupedDecisions};
