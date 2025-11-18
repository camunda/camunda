
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLogEntry} from 'modules/api/v2/auditLog/searchAuditLog';

type VariableDetails = {
  name: string;
  oldValue?: any;
  newValue: any;
  scope?: {
    id: string;
    name: string;
  };
};

type UserTaskDetails = {
  name: string;
  elementId?: string;
  assignee?: string;
  oldAssignee?: string;
  candidateGroups?: string;
  dueDate?: string;
  oldDueDate?: string;
  action?: string;
  variables?: Record<string, any>;
  allowOverride?: boolean;
};

export type MockAuditLogEntry = AuditLogEntry & {
  details?: {
    variable?: VariableDetails;
    userTask?: UserTaskDetails;
  };
  // Indicates the operation was applied to multiple process instances
  isMultiInstanceOperation?: boolean;
  // Optional number of affected instances when operation is multi-instance
  affectedInstancesCount?: number;
  // Error message for failed operations
  errorMessage?: string;
};

export const mockOperationLog: MockAuditLogEntry[] = [
  // Failed multi-instance operation
  {
    id: '28',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 10).toISOString(),
    user: 'Bulk Canceller',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 15,
    errorMessage: 'Request property [maxJobsToActivates] cannot be parsed.',
  },
  // Failed multi-instance operation (cancelled by user)
  {
    id: '27',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 30).toISOString(),
    user: 'Admin User',
    errorMessage: 'Operation was cancelled by the user before completion.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 8,
  },
  // Completed multi-instance operation
  {
    id: '26',
    processDefinitionName: 'Payment Process',
    processDefinitionVersion: 2,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 50).toISOString(),
    user: 'System Migration',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 42,
  },
  // Completed multi-instance operation
  {
    id: '25',
    processDefinitionName: 'Fulfillment Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 80).toISOString(),
    user: 'Incident Manager',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 5,
  },
  // Failed single instance operation
  {
    id: '24a',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 90).toISOString(),
    user: 'John Modifier',
    errorMessage: 'Cannot activate element "Confirm Order" because the target flow node has an active incident. Please resolve the incident before attempting to modify.',
  },
  {
    id: '24',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 100).toISOString(),
    user: 'Bulk Modifier',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 25,
  },
  {
    id: '23',
    processDefinitionName: 'Claims Process',
    processDefinitionVersion: 2,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 150).toISOString(),
    user: 'Bulk Migrator',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 12,
  },
  {
    id: '22',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 200).toISOString(),
    user: 'Incident Manager',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 7,
  },
  {
    id: '17',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 1000).toISOString(),
    user: 'Joshua Mover',
  },
  {
    id: '16',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 2000).toISOString(),
    user: 'Timothy Canceller',
  },
  {
    id: '15',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 3000).toISOString(),
    user: 'Tomas Adder',
  },
  {
    id: '14',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 4000).toISOString(),
    user: 'Kirk Varianc',
  },
  // Failed user task operation
  {
    id: '13a',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'ASSIGN_USER_TASK',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 4500).toISOString(),
    user: 'Sarah Manager',
    errorMessage: 'Failed to assign the user task to the user.',
    details: {
      userTask: {
        name: 'Process an order',
        elementId: 'UserTask_ProcessOrder',
        assignee: 'robert.johnson@example.com',
        oldAssignee: 'jane.doe@example.com',
        candidateGroups: 'Group A, Group B',
        dueDate: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        action: 'assign',
        allowOverride: false,
      },
    },
  },
  {
    id: '13',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'COMPLETE_USER_TASK',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 5000).toISOString(),
    user: 'John Doe',
    details: {
      userTask: {
        name: 'Process an order',
        elementId: 'UserTask_ProcessOrder',
        assignee: 'jane.doe@example.com',
        candidateGroups: 'Group A, Group B',
        dueDate: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        action: 'complete',
        variables: {
          orderStatus: 'processed',
          invoiceNumber: 'INV-98765',
        },
      },
    },
  },
  // UNASSIGN_USER_TASK example
  {
    id: '12b',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'UNASSIGN_USER_TASK',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 9000).toISOString(),
    user: 'Admin',
    details: {
      userTask: {
        name: 'Process an order',
        elementId: 'UserTask_ProcessOrder',
        oldAssignee: 'mike.jones@example.com',
        candidateGroups: 'Group C',
        dueDate: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(),
        action: 'unassign',
      },
    },
  },
  {
    id: '12',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'ASSIGN_USER_TASK',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 10000).toISOString(),
    user: 'Admin',
    details: {
      userTask: {
        name: 'Process an order',
        elementId: 'UserTask_ProcessOrder',
        assignee: 'eric.smith@example.com',
        oldAssignee: 'jane.doe@example.com',
        candidateGroups: 'Group C',
        dueDate: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(),
        action: 'assign',
        allowOverride: true,
      },
    },
  },
  // UPDATE_USER_TASK example
  {
    id: '11b',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'UPDATE_USER_TASK',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 12000).toISOString(),
    user: 'Admin',
    details: {
      userTask: {
        name: 'Process an order',
        elementId: 'UserTask_ProcessOrder',
        assignee: 'eric.smith@example.com',
        candidateGroups: 'Group C',
        dueDate: new Date(Date.now() + 72 * 60 * 60 * 1000).toISOString(),
        oldDueDate: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(),
        action: 'update',
      },
    },
  },
  {
    id: '11',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 15000).toISOString(),
    user: 'System Maintenance',
    errorMessage: 'Operation could not be completed because the system was undergoing maintenance. Please retry the operation.',
  },
  {
    id: '10',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 20000).toISOString(),
    endTimestamp: new Date(Date.now() - 20000).toISOString(),
    user: 'Charles Hernandez',
  },
  {
    id: '9',
    processDefinitionName: 'Decision Process',
    processDefinitionVersion: 1,
    processInstanceKey: '789',
    tenantId: 'default',
    operationType: 'UPDATE_VARIABLE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 25000).toISOString(),
    user: 'Karen Rodriguez',
    details: {
      variable: {
        name: 'priority',
        newValue: 'HIGH',
        scope: {id: 'updateCRM', name: 'Update CRM'},
      },
    },
  },
  {
    id: '7',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 35000).toISOString(),
    user: 'Susan Miller',
  },
  {
    id: '5',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'ADD_VARIABLE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 45000).toISOString(),
    user: 'David Brown',
    details: {
      variable: {
        name: 'orderId',
        newValue: 'ORD-12345',
        scope: {id: 'processOrder', name: 'Process Order'},
      },
    },
  },
  {
    id: '4',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'UPDATE_VARIABLE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 50000).toISOString(),
    user: 'Mary Williams',
    details: {
      variable: {
        name: 'customerAddress',
        oldValue: {street: 'Old Street', city: 'Old City'},
        newValue: {street: 'New Street', city: 'New City'},
        scope: {id: 'updateCustomerInfo', name: 'Update Customer Info'},
      },
    },
  },
  {
    id: '20',
    processDefinitionName: 'Claims Process',
    processDefinitionVersion: 2,
    processInstanceKey: '999',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 700).toISOString(),
    user: 'Maya Migrator',
  },
  {
    id: '19',
    processDefinitionName: 'Payment Process',
    processDefinitionVersion: 2,
    processInstanceKey: '456',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 800).toISOString(),
    user: 'Paul Payer',
  },
  {
    id: '18',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 900).toISOString(),
    user: 'Olivia Operator',
  },
  // CREATE_PROCESS_INSTANCE example
  {
    id: '21',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'CREATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 70000).toISOString(),
    user: 'order-service-client',
  },
  // EVALUATE_DECISION example
  {
    id: '30',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'EVALUATE_DECISION',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 32000).toISOString(),
    user: 'decision-engine-client',
  },
  {
    id: '3',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 55000).toISOString(),
    user: 'Peter Jones',
  },
  {
    id: '2',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 60000).toISOString(),
    user: 'Jane Smith',
  },
  {
    id: '1',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 65000).toISOString(),
    user: 'John Doe',
  },
];
