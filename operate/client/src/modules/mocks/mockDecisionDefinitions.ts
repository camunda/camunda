/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryDecisionDefinitionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';

const mockDecisionDefinitions: QueryDecisionDefinitionsResponseBody = {
  items: [
    {
      decisionDefinitionId: 'invoice-assign-approver',
      name: 'Assign Approver Group',
      version: 2,
      decisionRequirementsId: 'invoice-assign-approver-requirements',
      tenantId: '<default>',
      decisionDefinitionKey: '1',
      decisionRequirementsKey: '1-requirements',
    },
    {
      decisionDefinitionId: 'invoice-assign-approver',
      name: 'Assign Approver Group',
      version: 1,
      decisionRequirementsId: 'invoice-assign-approver-requirements',
      tenantId: '<default>',
      decisionDefinitionKey: '0',
      decisionRequirementsKey: '0-requirements',
    },
    {
      decisionDefinitionId: 'invoice-assign-approver',
      name: 'Assign Approver Group for tenant A',
      version: 3,
      decisionRequirementsId: 'invoice-assign-approver-tenant-A-requirements',
      tenantId: 'tenant-A',
      decisionDefinitionKey: '4',
      decisionRequirementsKey: '4-requirements',
    },
    {
      decisionDefinitionId: 'invoice-assign-approver',
      name: 'Assign Approver Group for tenant A',
      version: 2,
      decisionRequirementsId: 'invoice-assign-approver-tenant-A-requirements',
      tenantId: 'tenant-A',
      decisionDefinitionKey: '3',
      decisionRequirementsKey: '3-requirements',
    },
    {
      decisionDefinitionId: 'invoice-assign-approver',
      name: 'Assign Approver Group for tenant A',
      version: 1,
      decisionRequirementsId: 'invoice-assign-approver-tenant-A-requirements',
      tenantId: 'tenant-A',
      decisionDefinitionKey: '2',
      decisionRequirementsKey: '2-requirements',
    },
    {
      decisionDefinitionId: 'invoiceClassification',
      name: 'invoiceClassification',
      version: 1,
      decisionRequirementsId: 'invoiceClassification-requirements',
      tenantId: '<default>',
      decisionDefinitionKey: '5',
      decisionRequirementsKey: '5-requirements',
    },
    {
      decisionDefinitionId: 'calc-key-figures',
      name: 'Calculate Credit History Key Figures',
      version: 1,
      decisionRequirementsId: 'calc-key-figures-requirements',
      tenantId: '<default>',
      decisionDefinitionKey: '6',
      decisionRequirementsKey: '6-requirements',
    },
  ],
  page: {totalItems: 7},
};

export {mockDecisionDefinitions};
