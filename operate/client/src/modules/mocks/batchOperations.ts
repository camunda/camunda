/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type BatchOperationState =
  | 'CREATED'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'SUSPENDED'
  | 'CANCELLED'
  | 'FAILED';

export type BatchOperationType =
  | 'RESOLVE_INCIDENT'
  | 'MODIFY_PROCESS_INSTANCE'
  | 'MIGRATE_PROCESS_INSTANCE'
  | 'CANCEL_PROCESS_INSTANCE';

export type BatchOperationItemState =
  | 'ACTIVE'
  | 'COMPLETED'
  | 'SKIPPED'
  | 'CANCELLED'
  | 'FAILED';

export type BatchOperationItem = {
  id: string;
  processInstanceKey: string;
  processDefinitionName: string;
  processDefinitionVersion: number;
  state: BatchOperationItemState;
  timestamp: string;
  errorMessage?: string;
};

export type BatchOperation = {
  id: string;
  operationType: BatchOperationType;
  state: BatchOperationState;
  totalItems: number;
  completedItems: number;
  failedItems: number;
  startTime: string;
  endTime?: string;
  appliedBy: string;
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  errorMessage?: string;
  items: BatchOperationItem[];
};

// Mock batch operation items for detailed views
const generateMockItems = (
  count: number,
  operationState: BatchOperationState,
): BatchOperationItem[] => {
  const items: BatchOperationItem[] = [];
  const processNames = [
    'Order Process',
    'Payment Process',
    'Fulfillment Process',
    'Claims Process',
  ];

  // All possible item states for cycling through
  const allStates: BatchOperationItemState[] = [
    'COMPLETED',
    'FAILED',
    'ACTIVE',
    'SKIPPED',
    'CANCELLED',
  ];

  for (let i = 0; i < count; i++) {
    let itemState: BatchOperationItemState;

    if (operationState === 'ACTIVE') {
      itemState = i < count / 2 ? 'COMPLETED' : 'ACTIVE';
    } else if (operationState === 'COMPLETED') {
      // Show mixed statuses for completed operations to demonstrate all states
      itemState = allStates[i % allStates.length];
    } else if (operationState === 'PARTIALLY_COMPLETED') {
      itemState = i % 3 === 0 ? 'FAILED' : 'COMPLETED';
    } else if (operationState === 'FAILED') {
      itemState = 'FAILED';
    } else if (operationState === 'CANCELLED') {
      itemState = i < count / 3 ? 'COMPLETED' : 'CANCELLED';
    } else if (operationState === 'SUSPENDED') {
      // Show mix of states for suspended operations
      itemState = allStates[i % allStates.length];
    } else {
      itemState = 'ACTIVE';
    }

    items.push({
      id: `item-${i + 1}`,
      processInstanceKey: `225179981374904${i}`,
      processDefinitionName: processNames[i % processNames.length],
      processDefinitionVersion: (i % 3) + 1,
      state: itemState,
      timestamp: new Date(
        Date.now() - i * 60000 - Math.random() * 3600000,
      ).toISOString(),
      errorMessage:
        itemState === 'FAILED'
          ? 'Failed to execute operation - Process instance not found or already completed.'
          : undefined,
    });
  }

  return items;
};

export const mockBatchOperations: BatchOperation[] = [
  // Active - Migrate Process Instance
  {
    id: '2251799813688001',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'ACTIVE',
    totalItems: 120,
    completedItems: 45,
    failedItems: 0,
    startTime: '2025-11-20T11:11:11.000+0000',
    appliedBy: 'Admin',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 3,
    items: generateMockItems(120, 'ACTIVE'),
  },
  // Completed - Modify Process Instance (with some failures)
  {
    id: '2251799813688002',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 120,
    completedItems: 100,
    failedItems: 20,
    startTime: '2025-11-19T11:11:11.000+0000',
    endTime: '2025-11-19T12:20:00.000+0000',
    appliedBy: 'Admin',
    processDefinitionName: 'Payment Process',
    processDefinitionVersion: 2,
    items: generateMockItems(120, 'PARTIALLY_COMPLETED'),
  },
  // Suspended - Resolve Incidents
  {
    id: '2251799813688003',
    operationType: 'RESOLVE_INCIDENT',
    state: 'SUSPENDED',
    totalItems: 70,
    completedItems: 14,
    failedItems: 0,
    startTime: '2025-11-19T11:11:11.000+0000',
    appliedBy: 'John Doe',
    processDefinitionName: 'Claims Process',
    processDefinitionVersion: 2,
    items: generateMockItems(70, 'ACTIVE'),
  },
  // Cancelled - Resolve Incidents
  {
    id: '2251799813688004',
    operationType: 'RESOLVE_INCIDENT',
    state: 'CANCELLED',
    totalItems: 40,
    completedItems: 12,
    failedItems: 0,
    startTime: '2025-11-18T11:11:11.000+0000',
    endTime: '2025-11-18T11:30:00.000+0000',
    appliedBy: 'Maria Rotty',
    processDefinitionName: 'Fulfillment Process',
    processDefinitionVersion: 1,
    items: generateMockItems(40, 'CANCELLED'),
  },
  // Completed - Resolve Incidents
  {
    id: '2251799813688005',
    operationType: 'RESOLVE_INCIDENT',
    state: 'COMPLETED',
    totalItems: 56,
    completedItems: 56,
    failedItems: 0,
    startTime: '2025-11-18T11:11:11.000+0000',
    endTime: '2025-11-18T11:45:00.000+0000',
    appliedBy: 'Karl Boing',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 2,
    items: generateMockItems(56, 'COMPLETED'),
  },
  // Failed - Cancel Process Instance
  {
    id: '2251799813688006',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'FAILED',
    totalItems: 25,
    completedItems: 0,
    failedItems: 25,
    startTime: '2025-11-17T14:30:00.000+0000',
    endTime: '2025-11-17T14:31:00.000+0000',
    appliedBy: 'System Admin',
    processDefinitionName: 'Legacy Process',
    processDefinitionVersion: 1,
    errorMessage:
      'Batch operation failed: Target process instances are not in a valid state for cancellation.',
    items: generateMockItems(25, 'FAILED'),
  },
  // Partially Completed - Migrate Process Instance
  {
    id: '2251799813688007',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'PARTIALLY_COMPLETED',
    totalItems: 80,
    completedItems: 65,
    failedItems: 15,
    startTime: '2025-11-16T09:00:00.000+0000',
    endTime: '2025-11-16T09:45:00.000+0000',
    appliedBy: 'Deployment Service',
    processDefinitionName: 'Check in process',
    processDefinitionVersion: 2,
    items: generateMockItems(80, 'PARTIALLY_COMPLETED'),
  },
  // Created - Modify Process Instance
  {
    id: '2251799813688008',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    state: 'CREATED',
    totalItems: 50,
    completedItems: 0,
    failedItems: 0,
    startTime: '2025-11-20T12:00:00.000+0000',
    appliedBy: 'Batch Scheduler',
    processDefinitionName: 'Testing Process',
    processDefinitionVersion: 1,
    items: generateMockItems(50, 'CREATED'),
  },
  // Completed - Cancel Process Instance
  {
    id: '2251799813688009',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 35,
    completedItems: 35,
    failedItems: 0,
    startTime: '2025-11-15T16:20:00.000+0000',
    endTime: '2025-11-15T16:25:00.000+0000',
    appliedBy: 'Admin',
    processDefinitionName: 'Pricing Process',
    processDefinitionVersion: 1,
    items: generateMockItems(35, 'COMPLETED'),
  },
  // Active - Resolve Incident
  {
    id: '2251799813688010',
    operationType: 'RESOLVE_INCIDENT',
    state: 'ACTIVE',
    totalItems: 200,
    completedItems: 150,
    failedItems: 5,
    startTime: '2025-11-20T10:00:00.000+0000',
    appliedBy: 'Incident Manager',
    processDefinitionName: 'Decision Process',
    processDefinitionVersion: 1,
    items: generateMockItems(200, 'ACTIVE'),
  },
  // Completed - Migrate Process Instance (large batch)
  {
    id: '2251799813688011',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 500,
    completedItems: 498,
    failedItems: 2,
    startTime: '2025-11-14T08:00:00.000+0000',
    endTime: '2025-11-14T10:30:00.000+0000',
    appliedBy: 'DevOps Team',
    processDefinitionName: 'Customer Onboarding',
    processDefinitionVersion: 4,
    items: generateMockItems(500, 'COMPLETED'),
  },
  // Suspended - Modify Process Instance
  {
    id: '2251799813688012',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    state: 'SUSPENDED',
    totalItems: 90,
    completedItems: 30,
    failedItems: 2,
    startTime: '2025-11-19T15:45:00.000+0000',
    appliedBy: 'Sarah Connor',
    processDefinitionName: 'Risk Assessment',
    processDefinitionVersion: 1,
    items: generateMockItems(90, 'SUSPENDED'),
  },
  // Completed - Cancel Process Instance (small batch)
  {
    id: '2251799813688013',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 5,
    completedItems: 5,
    failedItems: 0,
    startTime: '2025-11-13T11:20:00.000+0000',
    endTime: '2025-11-13T11:21:00.000+0000',
    appliedBy: 'QA Engineer',
    processDefinitionName: 'Testing Process',
    processDefinitionVersion: 3,
    items: generateMockItems(5, 'COMPLETED'),
  },
  // Failed - Migrate Process Instance
  {
    id: '2251799813688014',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'FAILED',
    totalItems: 150,
    completedItems: 0,
    failedItems: 150,
    startTime: '2025-11-12T09:15:00.000+0000',
    endTime: '2025-11-12T09:16:00.000+0000',
    appliedBy: 'Migration Service',
    processDefinitionName: 'Legacy Workflow',
    processDefinitionVersion: 1,
    errorMessage:
      'Migration failed: Target process definition not found or incompatible schema version.',
    items: generateMockItems(150, 'FAILED'),
  },
  // Partially Completed - Resolve Incident
  {
    id: '2251799813688015',
    operationType: 'RESOLVE_INCIDENT',
    state: 'PARTIALLY_COMPLETED',
    totalItems: 45,
    completedItems: 38,
    failedItems: 7,
    startTime: '2025-11-11T14:00:00.000+0000',
    endTime: '2025-11-11T14:35:00.000+0000',
    appliedBy: 'Support Team',
    processDefinitionName: 'Invoice Processing',
    processDefinitionVersion: 2,
    items: generateMockItems(45, 'PARTIALLY_COMPLETED'),
  },
  // Completed - Modify Process Instance
  {
    id: '2251799813688016',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 75,
    completedItems: 75,
    failedItems: 0,
    startTime: '2025-11-10T16:30:00.000+0000',
    endTime: '2025-11-10T17:00:00.000+0000',
    appliedBy: 'Admin',
    processDefinitionName: 'Shipping Workflow',
    processDefinitionVersion: 3,
    items: generateMockItems(75, 'COMPLETED'),
  },
  // Cancelled - Migrate Process Instance
  {
    id: '2251799813688017',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'CANCELLED',
    totalItems: 200,
    completedItems: 85,
    failedItems: 0,
    startTime: '2025-11-09T08:00:00.000+0000',
    endTime: '2025-11-09T08:45:00.000+0000',
    appliedBy: 'Release Manager',
    processDefinitionName: 'Approval Workflow',
    processDefinitionVersion: 2,
    items: generateMockItems(200, 'CANCELLED'),
  },
  // Active - Cancel Process Instance
  {
    id: '2251799813688018',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'ACTIVE',
    totalItems: 60,
    completedItems: 22,
    failedItems: 0,
    startTime: '2025-11-20T13:30:00.000+0000',
    appliedBy: 'System Cleanup',
    processDefinitionName: 'Expired Sessions',
    processDefinitionVersion: 1,
    items: generateMockItems(60, 'ACTIVE'),
  },
  // Completed - Resolve Incident (all successful)
  {
    id: '2251799813688019',
    operationType: 'RESOLVE_INCIDENT',
    state: 'COMPLETED',
    totalItems: 12,
    completedItems: 12,
    failedItems: 0,
    startTime: '2025-11-08T10:15:00.000+0000',
    endTime: '2025-11-08T10:18:00.000+0000',
    appliedBy: 'Jane Smith',
    processDefinitionName: 'Email Notification',
    processDefinitionVersion: 1,
    items: generateMockItems(12, 'COMPLETED'),
  },
  // Partially Completed - Cancel Process Instance
  {
    id: '2251799813688020',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'PARTIALLY_COMPLETED',
    totalItems: 100,
    completedItems: 72,
    failedItems: 28,
    startTime: '2025-11-07T09:00:00.000+0000',
    endTime: '2025-11-07T09:45:00.000+0000',
    appliedBy: 'Operations Lead',
    processDefinitionName: 'Data Sync Process',
    processDefinitionVersion: 2,
    items: generateMockItems(100, 'PARTIALLY_COMPLETED'),
  },
  // Created - Resolve Incident (pending start)
  {
    id: '2251799813688021',
    operationType: 'RESOLVE_INCIDENT',
    state: 'CREATED',
    totalItems: 35,
    completedItems: 0,
    failedItems: 0,
    startTime: '2025-11-20T14:00:00.000+0000',
    appliedBy: 'Scheduler Bot',
    processDefinitionName: 'Alert Handler',
    processDefinitionVersion: 1,
    items: generateMockItems(35, 'CREATED'),
  },
  // Completed - Migrate Process Instance (medium batch)
  {
    id: '2251799813688022',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 180,
    completedItems: 175,
    failedItems: 5,
    startTime: '2025-11-06T11:30:00.000+0000',
    endTime: '2025-11-06T12:45:00.000+0000',
    appliedBy: 'Platform Team',
    processDefinitionName: 'User Registration',
    processDefinitionVersion: 5,
    items: generateMockItems(180, 'COMPLETED'),
  },
  // Suspended - Cancel Process Instance
  {
    id: '2251799813688023',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'SUSPENDED',
    totalItems: 55,
    completedItems: 20,
    failedItems: 1,
    startTime: '2025-11-19T17:00:00.000+0000',
    appliedBy: 'Emergency Stop',
    processDefinitionName: 'Bulk Import',
    processDefinitionVersion: 1,
    items: generateMockItems(55, 'SUSPENDED'),
  },
  // Failed - Modify Process Instance
  {
    id: '2251799813688024',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    state: 'FAILED',
    totalItems: 30,
    completedItems: 0,
    failedItems: 30,
    startTime: '2025-11-05T13:00:00.000+0000',
    endTime: '2025-11-05T13:02:00.000+0000',
    appliedBy: 'Script Runner',
    processDefinitionName: 'Document Archive',
    processDefinitionVersion: 2,
    errorMessage:
      'Modification failed: Required variables missing or invalid variable type.',
    items: generateMockItems(30, 'FAILED'),
  },
  // Completed - Resolve Incident (with some failures)
  {
    id: '2251799813688025',
    operationType: 'RESOLVE_INCIDENT',
    state: 'COMPLETED',
    totalItems: 88,
    completedItems: 80,
    failedItems: 8,
    startTime: '2025-11-04T15:30:00.000+0000',
    endTime: '2025-11-04T16:15:00.000+0000',
    appliedBy: 'Night Shift',
    processDefinitionName: 'Batch Processing',
    processDefinitionVersion: 3,
    items: generateMockItems(88, 'PARTIALLY_COMPLETED'),
  },
  // Completed - Migrate Process Instance (large batch with >1K items)
  {
    id: '2251799813688026',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    state: 'COMPLETED',
    totalItems: 2500,
    completedItems: 2340,
    failedItems: 160,
    startTime: '2025-11-03T06:00:00.000+0000',
    endTime: '2025-11-03T08:30:00.000+0000',
    appliedBy: 'Data Migration Team',
    processDefinitionName: 'Enterprise Workflow',
    processDefinitionVersion: 6,
    items: generateMockItems(100, 'COMPLETED'), // Using smaller item count for mock
  },
  // Partially Completed - Cancel Process Instance (very large batch with >1K items)
  {
    id: '2251799813688027',
    operationType: 'CANCEL_PROCESS_INSTANCE',
    state: 'PARTIALLY_COMPLETED',
    totalItems: 15000,
    completedItems: 12450,
    failedItems: 1830,
    startTime: '2025-11-02T00:00:00.000+0000',
    endTime: '2025-11-02T06:45:00.000+0000',
    appliedBy: 'Year-End Cleanup',
    processDefinitionName: 'Quarterly Reports',
    processDefinitionVersion: 2,
    items: generateMockItems(100, 'PARTIALLY_COMPLETED'), // Using smaller item count for mock
  },
];

