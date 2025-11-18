/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLogEntry} from 'modules/api/v2/auditLog/searchAuditLog';

// Extended type for mock data with additional fields
export type MockAuditLogEntry = AuditLogEntry & {
  details?: {
    status?: string;
    modifications?: {
      activateInstructions: {
        elementId: string;
        elementName?: string;
        variableInstructions?: {
          variables: any;
          scopeId: string;
        }[];
        ancestorElementInstanceKey?: string;
      }[];
      terminateInstructions: {
        elementInstanceKey: string;
        elementName?: string;
      }[];
    };
    migrationPlan?: {
      mappingInstructions: {
        sourceElementId: string;
        sourceElementName?: string;
        targetElementId: string;
        targetElementName?: string;
      }[];
      targetProcessDefinitionKey: string;
      targetProcessDefinitionName?: string;
    };
    variable?: {
      name: string;
      oldValue?: any;
      newValue: any;
      scope?: {
        id: string;
        name: string;
      };
    };
    resourceKey?: string;
    resourceType?: string;
  };
  isMultiInstanceOperation?: boolean;
  batchOperationId?: string;
  affectedInstancesCount?: number;
  errorMessage?: string;
};

export const mockAuditLogEntries: MockAuditLogEntry[] = [
  // Batch operation - Migrate Process Instance
  {
    id: 'batch-migrate-1',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 3,
    tenantId: '<default>',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-16T14:30:00.000+0000',
    endTimestamp: '2024-01-16T14:35:45.000+0000',
    user: 'System Admin',
    isMultiInstanceOperation: true,
    batchOperationId: '2251799813688001',
    affectedInstancesCount: 127,
  },
  // Batch operation - Cancel Process Instance
  {
    id: 'batch-cancel-1',
    processDefinitionName: 'Payment Process',
    processDefinitionVersion: 2,
    tenantId: '<default>',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-16T11:15:00.000+0000',
    endTimestamp: '2024-01-16T11:18:22.000+0000',
    user: 'Michael Scott',
    isMultiInstanceOperation: true,
    batchOperationId: '2251799813688002',
    affectedInstancesCount: 43,
  },
  // Batch operation - Resolve Incident
  {
    id: 'batch-resolve-1',
    processDefinitionName: 'Claims Process',
    processDefinitionVersion: 2,
    tenantId: 'tenant-a',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: '2024-01-16T09:00:00.000+0000',
    endTimestamp: '2024-01-16T09:12:30.000+0000',
    user: 'Incident Manager',
    isMultiInstanceOperation: true,
    batchOperationId: '2251799813688003',
    affectedInstancesCount: 89,
  },
  // Batch operation - Modify Process Instance
  {
    id: 'batch-modify-1',
    processDefinitionName: 'Fulfillment Process',
    processDefinitionVersion: 1,
    tenantId: '<default>',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-15T16:45:00.000+0000',
    endTimestamp: '2024-01-15T16:52:15.000+0000',
    user: 'Dwight Schrute',
    isMultiInstanceOperation: true,
    batchOperationId: '2251799813688004',
    affectedInstancesCount: 65,
  },
  // Batch operation - Failed
  {
    id: 'batch-fail-1',
    processDefinitionName: 'Process C',
    processDefinitionVersion: 2,
    tenantId: 'tenant-b',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'fail',
    startTimestamp: '2024-01-15T13:20:00.000+0000',
    endTimestamp: '2024-01-15T13:25:00.000+0000',
    user: 'Andy Bernard',
    isMultiInstanceOperation: true,
    batchOperationId: '2251799813688005',
    affectedInstancesCount: 12,
    errorMessage: 'Target process definition not found or incompatible with source.',
  },
  // Create process instance
  {
    id: 'create-1',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 3,
    processInstanceKey: '2251799813685297',
    processInstanceState: 'ACTIVE',
    tenantId: '<default>',
    operationType: 'CREATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-15T10:30:00.000+0000',
    endTimestamp: '2024-01-15T10:30:01.000+0000',
    user: 'order-api-client',
  },
  // Evaluate decision
  {
    id: 'eval-1',
    processDefinitionName: 'Decision Process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685303',
    processInstanceState: 'ACTIVE',
    tenantId: '<default>',
    operationType: 'EVALUATE_DECISION',
    operationState: 'success',
    startTimestamp: '2024-01-15T10:15:00.000+0000',
    endTimestamp: '2024-01-15T10:15:01.000+0000',
    user: 'decision-service-client',
  },
  // Deploy resource - Process definition
  {
    id: 'deploy-1',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 4,
    tenantId: '<default>',
    operationType: 'DEPLOY_RESOURCE',
    operationState: 'success',
    startTimestamp: '2024-01-15T08:00:00.000+0000',
    endTimestamp: '2024-01-15T08:00:02.000+0000',
    user: 'deployment-service-client',
    details: {
      resourceKey: '2251799813685400',
      resourceType: 'process-definition',
    },
  },
  // Deploy resource - Form
  {
    id: 'deploy-2',
    processDefinitionName: 'Customer Registration Form',
    processDefinitionVersion: 1,
    tenantId: '<default>',
    operationType: 'DEPLOY_RESOURCE',
    operationState: 'success',
    startTimestamp: '2024-01-14T22:15:00.000+0000',
    endTimestamp: '2024-01-14T22:15:01.000+0000',
    user: 'admin-user',
    details: {
      resourceKey: '2251799813685401',
      resourceType: 'form',
    },
  },
  // Delete resource - Process definition
  {
    id: 'delete-resource-1',
    processDefinitionName: 'Legacy Process',
    processDefinitionVersion: 1,
    tenantId: 'tenant-a',
    operationType: 'DELETE_RESOURCE',
    operationState: 'success',
    startTimestamp: '2024-01-14T20:30:00.000+0000',
    endTimestamp: '2024-01-14T20:30:01.000+0000',
    user: 'System Admin',
    details: {
      resourceKey: '2251799813685402',
      resourceType: 'process-definition',
    },
  },
  // Delete resource - Failed
  {
    id: 'delete-resource-2',
    processDefinitionName: 'Active Process',
    processDefinitionVersion: 2,
    tenantId: '<default>',
    operationType: 'DELETE_RESOURCE',
    operationState: 'fail',
    startTimestamp: '2024-01-14T19:45:00.000+0000',
    endTimestamp: '2024-01-14T19:45:01.000+0000',
    user: 'admin-user',
    errorMessage: 'Cannot delete resource: Active process instances exist.',
    details: {
      resourceKey: '2251799813685403',
      resourceType: 'process-definition',
    },
  },
  // Add variable operation
  {
    id: 'add-var-1',
    processDefinitionName: 'Pricing Process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685298',
    processInstanceState: 'ACTIVE',
    tenantId: '<default>',
    operationType: 'ADD_VARIABLE',
    operationState: 'success',
    startTimestamp: '2024-01-14T18:30:00.000+0000',
    endTimestamp: '2024-01-14T18:30:01.000+0000',
    user: 'pricing-service-client',
    details: {
      variable: {
        name: 'discountRate',
        newValue: 0.15,
        scope: {
          id: 'pricingProcess',
          name: 'Pricing Process',
        },
      },
    },
  },
  {
    id: '1',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 2,
    processInstanceKey: '2251799813685248',
    processInstanceState: 'ACTIVE',
    tenantId: '<default>',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-14T17:51:12.000+0000',
    endTimestamp: '2024-01-14T17:52:15.000+0000',
    user: 'Andy Bernard',
  },
  {
    id: '2',
    processDefinitionName: 'Check in process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685249',
    processInstanceState: 'COMPLETED',
    tenantId: '<default>',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-14T14:41:23.000+0000',
    endTimestamp: '2024-01-14T14:42:35.000+0000',
    user: 'Dwight Schrute',
  },
  {
    id: '3',
    processDefinitionName: 'Check in process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685250',
    processInstanceState: 'CANCELED',
    tenantId: 'tenant-a',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-14T11:01:34.000+0000',
    endTimestamp: '2024-01-14T11:01:35.000+0000',
    user: 'Michael Scott',
  },
  {
    id: '4',
    processDefinitionName: 'process d',
    processDefinitionVersion: 3,
    processInstanceKey: '2251799813685251',
    processInstanceState: 'INCIDENT',
    tenantId: '<default>',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: '2024-01-13T09:51:45.000+0000',
    endTimestamp: '2024-01-13T09:52:10.000+0000',
    user: 'Ryan Howard',
  },
  {
    id: '5',
    processDefinitionName: 'Process C',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685252',
    processInstanceState: 'COMPLETED',
    tenantId: 'tenant-b',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Toby Flenderson',
  },
  {
    id: '6',
    processDefinitionName: 'another process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685253',
    processInstanceState: 'COMPLETED',
    tenantId: '<default>',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Kelly Kapoor',
  },
  {
    id: '7',
    processDefinitionName: 'testing process',
    processDefinitionVersion: 2,
    processInstanceKey: '2251799813685254',
    processInstanceState: 'COMPLETED',
    tenantId: 'tenant-a',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Toby Flenderson',
  },
  {
    id: '8',
    processDefinitionName: 'Process C',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685255',
    processInstanceState: 'ACTIVE',
    tenantId: '<default>',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Oscar Martinez',
  },
  {
    id: '9',
    processDefinitionName: 'B process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685256',
    processInstanceState: 'COMPLETED',
    tenantId: 'tenant-b',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Toby Flenderson',
  },
  {
    id: '10',
    processDefinitionName: 'A test process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685257',
    processInstanceState: 'TERMINATED',
    tenantId: '<default>',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'fail',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Robert California',
    errorMessage: 'Unable to resolve incident: The process instance has been terminated and incidents can no longer be resolved.',
  },
  {
    id: '12',
    processDefinitionName: 'Check in process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685259',
    processInstanceState: 'ACTIVE',
    tenantId: 'tenant-a',
    operationType: 'ADD_VARIABLE',
    operationState: 'success',
    startTimestamp: '2024-01-12T13:33:57.000+0000',
    endTimestamp: '2024-01-12T13:35:12.000+0000',
    user: 'Dwight Schrute',
    details: {
      variable: {
        name: 'guestName',
        newValue: 'John Smith',
        scope: {
          id: 'checkInProcess',
          name: 'Check In Process',
        },
      },
    },
  },
  {
    id: '13',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '2251799813685260',
    processInstanceState: 'ACTIVE',
    tenantId: '<default>',
    operationType: 'UPDATE_VARIABLE',
    operationState: 'success',
    startTimestamp: '2024-01-11T13:33:57.000+0000',
    endTimestamp: '2024-01-11T13:35:12.000+0000',
    user: 'Creed Bratton',
    details: {
      variable: {
        name: 'orderStatus',
        oldValue: 'pending',
        newValue: 'approved',
        scope: {
          id: 'orderProcess',
          name: 'Order Process',
        },
      },
    },
  },
];
