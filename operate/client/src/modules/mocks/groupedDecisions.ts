/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DecisionDto} from 'modules/api/decisions/fetchGroupedDecisions';

const groupedDecisions: DecisionDto[] = [
  {
    decisionId: 'invoice-assign-approver',
    tenantId: '<default>',
    name: 'Assign Approver Group',
    decisions: [
      {
        id: '1',
        version: 2,
        decisionId: 'invoice-assign-approver',
      },
      {
        id: '0',
        version: 1,
        decisionId: 'invoice-assign-approver',
      },
    ],
    permissions: ['READ'],
  },
  {
    decisionId: 'invoice-assign-approver',
    tenantId: 'tenant-A',
    name: 'Assign Approver Group for tenant A',
    decisions: [
      {
        id: '4',
        version: 3,
        decisionId: 'invoice-assign-approver',
      },
      {
        id: '3',
        version: 2,
        decisionId: 'invoice-assign-approver',
      },
      {
        id: '2',
        version: 1,
        decisionId: 'invoice-assign-approver',
      },
    ],
    permissions: ['READ'],
  },
  {
    decisionId: 'invoiceClassification',
    tenantId: '<default>',
    name: null,
    decisions: [
      {
        id: '5',
        version: 1,
        decisionId: 'invoiceClassification',
      },
    ],
    permissions: ['READ', 'DELETE'],
  },
  {
    decisionId: 'calc-key-figures',
    tenantId: '<default>',
    name: 'Calculate Credit History Key Figures',
    decisions: [
      {
        id: '6',
        version: 1,
        decisionId: 'calc-key-figures',
      },
    ],
    permissions: ['READ'],
  },
];

export {groupedDecisions};
