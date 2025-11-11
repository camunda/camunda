
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLogEntry} from 'modules/api/v2/auditLog/searchAuditLog';

type ModificationDetails = {
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

type MigrationDetails = {
  mappingInstructions: {
    sourceElementId: string;
    sourceElementName?: string;
    targetElementId: string;
    targetElementName?: string;
  }[];
  targetProcessDefinitionKey: string;
  targetProcessDefinitionName?: string;
};

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
  candidateGroups?: string;
  dueDate?: string;
  action?: string;
  variables?: Record<string, any>;
  allowOverride?: boolean;
};

export type MockAuditLogEntry = AuditLogEntry & {
  details?: {
    status?: string; // for incident resolution
    modifications?: ModificationDetails; // for modification
    migrationPlan?: MigrationDetails; // for migration
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
    operationState: 'FAILED',
    startTimestamp: new Date(Date.now() - 10).toISOString(),
    user: 'Bulk Canceller',
    comment: 'Attempted to cancel multiple instances but failed.',
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
    operationState: 'FAILED',
    startTimestamp: new Date(Date.now() - 30).toISOString(),
    user: 'Admin User',
    comment: 'Modification operation was cancelled by the user.',
    errorMessage: 'Operation was cancelled by the user before completion.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 8,
    details: {
      modifications: {
        activateInstructions: [
          {
            elementId: 'Activity_SendNotification',
            elementName: 'Send Notification',
          },
        ],
        terminateInstructions: [],
      },
    },
  },
  // Completed multi-instance operation
  {
    id: '26',
    processDefinitionName: 'Payment Process',
    processDefinitionVersion: 2,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 50).toISOString(),
    user: 'System Migration',
    comment: 'Migration completed across multiple instances.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 42,
    details: {
      migrationPlan: {
        targetProcessDefinitionKey: '7788990011',
        targetProcessDefinitionName: 'Payment Process v4',
        mappingInstructions: [
          {
            sourceElementId: 'Activity_ProcessPayment',
            sourceElementName: 'Process Payment',
            targetElementId: 'Activity_ProcessPayment_v4',
            targetElementName: 'Process Payment (new gateway)',
          },
        ],
      },
    },
  },
  // Completed multi-instance operation
  {
    id: '25',
    processDefinitionName: 'Fulfillment Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 80).toISOString(),
    user: 'Incident Manager',
    comment: 'Incident resolution completed - incidents already resolved.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 5,
    details: {
      status: 'RESOLVED',
    },
  },
  // Failed single instance operation
  {
    id: '24a',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'FAILED',
    startTimestamp: new Date(Date.now() - 90).toISOString(),
    user: 'John Modifier',
    comment: 'Attempted to modify process instance.',
    errorMessage: 'Cannot activate element "Confirm Order" because the target flow node has an active incident. Please resolve the incident before attempting to modify.',
    details: {
      modifications: {
        activateInstructions: [
          {
            elementId: '2251734813681235',
            elementName: 'Confirm Order',
          },
        ],
        terminateInstructions: [],
      },
    },
  },
  {
    id: '24',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 100).toISOString(),
    user: 'Bulk Modifier',
    comment: 'Applied variable update to a set of instances.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 25,
    details: {
      modifications: {
        activateInstructions: [
          {
            elementId: 'Activity_UpdateCRM',
            elementName: 'Update CRM',
            variableInstructions: [
              {
                variables: {priority: 'HIGH'},
                scopeId: 'Activity_UpdateCRM',
              },
            ],
          },
        ],
        terminateInstructions: [],
      },
    },
  },
  {
    id: '23',
    processDefinitionName: 'Claims Process',
    processDefinitionVersion: 2,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MIGRATE_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 150).toISOString(),
    user: 'Bulk Migrator',
    comment: 'Migration executed across multiple running instances.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 12,
    details: {
      migrationPlan: {
        targetProcessDefinitionKey: '9988776655',
        targetProcessDefinitionName: 'Claims Process v4',
        mappingInstructions: [
          {
            sourceElementId: 'Task_AssessClaim',
            sourceElementName: 'Assess Claim',
            targetElementId: 'Task_AssessClaim_v3',
            targetElementName: 'Assess Claim (enhanced v3)',
          },
        ],
      },
    },
  },
  {
    id: '22',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'RESOLVE_INCIDENT',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 200).toISOString(),
    user: 'Incident Manager',
    comment: 'Resolved incidents across a batch of instances.',
    isMultiInstanceOperation: true,
    affectedInstancesCount: 7,
    details: {
      status: 'RESOLVED',
    },
  },
  {
    id: '17',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 1000).toISOString(),
    user: 'Joshua Mover',
    comment: 'Move operation: from Process Payment to Confirm Order',
    details: {
      modifications: {
        activateInstructions: [
          {
            elementId: '2251734813681235',
            elementName: 'Confirm Order',
          },
        ],
        terminateInstructions: [
          {
            elementInstanceKey: '2251799813686799',
            elementName: 'Process Payment',
          },
        ],
      },
    },
  },
  {
    id: '16',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 2000).toISOString(),
    user: 'Timothy Canceller',
    comment: 'Cancel operation: terminate all running instances of Check Inventory',
    details: {
      modifications: {
        activateInstructions: [],
        terminateInstructions: [
          {
            elementInstanceKey: '2251799813686800',
            elementName: 'Check Inventory',
          },
          {
            elementInstanceKey: '2251799813686801',
            elementName: 'Warehouse Analysis',
          },
        ],
      },
    },
  },
  {
    id: '15',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 3000).toISOString(),
    user: 'Tomas Adder',
    comment: 'Add operation: new instance of Notify Customer with variables',
    details: {
      modifications: {
        activateInstructions: [
          {
            elementId: '224566794523486565',
            elementName: 'Notify Customer',
            variableInstructions: [
              {
                variables: {
                  newItem: 'item-123',
                  approvedBy: 'User Add',
                },
                scopeId: 'notifyCustomer',
              },
            ],
          },
        ],
        terminateInstructions: [],
      },
    },
  },
  {
    id: '14',
    processDefinitionName: 'Order Process',
    processDefinitionVersion: 1,
    processInstanceKey: '123',
    tenantId: 'default',
    operationType: 'MODIFY_PROCESS_INSTANCE',
    operationState: 'COMPLETED',
    startTimestamp: new Date(Date.now() - 4000).toISOString(),
    user: 'Kirk Varianc',
    comment: 'Variable modification: editing variables in the scope of Update CRM',
    details: {
      modifications: {
        activateInstructions: [
          {
            elementId: '3434534465556143',
            elementName: 'Update CRM',
            variableInstructions: [
              {
                variables: {
                  status: 'reviewed',
                  prio: 1,
                },
                scopeId: 'updateCRM',
              },
            ],
          },
        ],
        terminateInstructions: [],
      },
    },
  },
    // Failed user task operation
    {
      id: '13a',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'ASSIGN_USER_TASK',
      operationState: 'FAILED',
      startTimestamp: new Date(Date.now() - 4500).toISOString(),
      user: 'Sarah Manager',
      comment: 'Attempted to assign task to a user.',
      errorMessage: 'The user does not have the required permissions to be assigned this task.',
      details: {
        userTask: {
          name: 'Process an order',
          elementId: 'UserTask_ProcessOrder',
          assignee: 'robert.johnson@example.com',
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
      operationState: 'COMPLETED',
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
    {
      id: '12',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'ASSIGN_USER_TASK',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 10000).toISOString(),
      user: 'Admin',
      comment: 'Task assigned to specialist.',
      details: {
        userTask: {
          name: 'Process an order',
          elementId: 'UserTask_ProcessOrder',
          assignee: 'eric.smith@example.com',
          candidateGroups: 'Group C',
          dueDate: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(),
          action: 'assign',
          allowOverride: true,
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
      operationState: 'FAILED',
      startTimestamp: new Date(Date.now() - 15000).toISOString(),
      user: 'System Maintenance',
      comment: 'Operation failed due to system maintenance.',
      errorMessage: 'Operation could not be completed because the system was undergoing maintenance. Please retry the operation.',
      details: {
        modifications: {
          activateInstructions: [{elementId: 'Gateway_1', elementName: 'Exclusive Gateway'}],
          terminateInstructions: [],
        },
      },
    },
    {
      id: '10',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'MODIFY_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 20000).toISOString(),
      endTimestamp: new Date(Date.now() - 20000).toISOString(),
      user: 'Charles Hernandez',
      comment: 'Applied complex modification to fix stuck instance.',
      details: {
        modifications: {
          activateInstructions: [
            {
              elementId: 'ServiceTask_3',
              elementName: 'Process Payment',
            },
            {
              elementId: 'UserTask_4',
              elementName: 'Ship Goods',
              variableInstructions: [
                {
                  variables: {approved: true},
                  scopeId: 'UserTask_4',
                },
              ],
            },
          ],
          terminateInstructions: [
            {
              elementInstanceKey: '2251799813686999',
              elementName: 'Review Order',
            },
            {
              elementInstanceKey: '2251799813686998',
              elementName: 'Pack Goods',
            },
          ],
        },
      },
    },
    {
      id: '9',
      processDefinitionName: 'Decision Process',
      processDefinitionVersion: 1,
      processInstanceKey: '789',
      tenantId: 'default',
      operationType: 'UPDATE_VARIABLE',
      operationState: 'COMPLETED',
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
      id: '8',
      processDefinitionName: 'Decision Process',
      processDefinitionVersion: 1,
      processInstanceKey: '789',
      tenantId: 'default',
      operationType: 'DELETE_DECISION_DEFINITION',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 30000).toISOString(),
      endTimestamp: new Date(Date.now() - 30000).toISOString(),
      user: 'Richard Garcia',
      comment: 'Decision definition successfully deleted.',
    },
    {
      id: '7',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'CANCEL_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 35000).toISOString(),
      user: 'Susan Miller',
      comment: 'Process instance successfully cancelled.',
    },
    {
      id: '6',
      processDefinitionName: 'Payment Process',
      processDefinitionVersion: 2,
      processInstanceKey: '456',
      tenantId: 'default',
      operationType: 'DELETE_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 40000).toISOString(),
      endTimestamp: new Date(Date.now() - 40000).toISOString(),
      user: 'System Process',
      comment: 'Failed to delete instance due to active incidents.',
    },
    {
      id: '5',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'ADD_VARIABLE',
      operationState: 'COMPLETED',
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
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 50000).toISOString(),
      user: 'Mary Williams',
      comment: 'Customer provided new address.',
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
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 700).toISOString(),
      user: 'Maya Migrator',
      details: {
        migrationPlan: {
          targetProcessDefinitionKey: '4455667788',
          targetProcessDefinitionName: 'Claims Process v3',
          mappingInstructions: [
            {
              sourceElementId: 'Task_SubmitClaim',
              sourceElementName: 'Submit Claim',
              targetElementId: 'Task_SubmitClaim',
              targetElementName: 'Submit Claim',
            },
            {
              sourceElementId: 'Task_AssessClaim',
              sourceElementName: 'Assess Claim',
              targetElementId: 'Task_AssessClaim_v2',
              targetElementName: 'Assess Claim (enhanced)',
            },
            {
              sourceElementId: 'Task_RequestDocuments',
              sourceElementName: 'Request Documents',
              targetElementId: 'Task_RequestDocuments',
              targetElementName: 'Request Documents',
            },
            {
              sourceElementId: 'Task_ValidateDocuments',
              sourceElementName: 'Validate Documents',
              targetElementId: 'Task_ValidateDocuments_v2',
              targetElementName: 'Validate Documents (OCR)',
            },
            {
              sourceElementId: 'Gateway_FraudCheck',
              sourceElementName: 'Fraud Check',
              targetElementId: 'Gateway_FraudCheck',
              targetElementName: 'Fraud Check',
            },
            {
              sourceElementId: 'Task_ManualReview',
              sourceElementName: 'Manual Review',
              targetElementId: 'Task_ManualReview',
              targetElementName: 'Manual Review',
            },
            {
              sourceElementId: 'Task_DecidePayout',
              sourceElementName: 'Decide Payout',
              targetElementId: 'Task_DecidePayout_v2',
              targetElementName: 'Decide Payout (rules v2)',
            },
            {
              sourceElementId: 'Task_NotifyCustomer',
              sourceElementName: 'Notify Customer',
              targetElementId: 'Task_NotifyCustomer',
              targetElementName: 'Notify Customer',
            },
            {
              sourceElementId: 'Task_ArchiveCase',
              sourceElementName: 'Archive Case',
              targetElementId: 'Task_ArchiveCase',
              targetElementName: 'Archive Case',
            },
            {
              sourceElementId: 'Event_PayoutSent',
              sourceElementName: 'Payout Sent',
              targetElementId: 'Event_PayoutSent',
              targetElementName: 'Payout Sent',
            },
          ],
        },
      },
    },
    {
      id: '19',
      processDefinitionName: 'Payment Process',
      processDefinitionVersion: 2,
      processInstanceKey: '456',
      tenantId: 'default',
      operationType: 'MIGRATE_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 800).toISOString(),
      user: 'Paul Payer',
      details: {
        migrationPlan: {
          targetProcessDefinitionKey: '6677889900',
          targetProcessDefinitionName: 'Payment Process v3',
          mappingInstructions: [
            {
              sourceElementId: 'Activity_ChargeCard',
              sourceElementName: 'Charge Card',
              targetElementId: 'Activity_ChargeCard_v2',
              targetElementName: 'Charge Card (3DS)',
            },
            {
              sourceElementId: 'Activity_SendReceipt',
              sourceElementName: 'Send Receipt',
              targetElementId: 'Activity_SendReceipt',
              targetElementName: 'Send Receipt',
            },
          ],
        },
      },
    },
    {
      id: '18',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'MIGRATE_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 900).toISOString(),
      user: 'Olivia Operator',
      details: {
        migrationPlan: {
          targetProcessDefinitionKey: '2251799813686750',
          targetProcessDefinitionName: 'Order Process v4',
          mappingInstructions: [
            {
              sourceElementId: 'Activity_ValidateOrder',
              sourceElementName: 'Validate Order',
              targetElementId: 'Activity_ValidateOrder',
              targetElementName: 'Validate Order',
            },
            {
              sourceElementId: 'Activity_ShipGoods',
              sourceElementName: 'Ship Goods',
              targetElementId: 'Activity_ShipGoods_v2',
              targetElementName: 'Ship Goods (carrier API)',
            },
          ],
        },
      },
    },
    {
      id: '3',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'MIGRATE_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 55000).toISOString(),
      user: 'Peter Jones',
      details: {
        migrationPlan: {
          targetProcessDefinitionKey: '2251799813686749',
          targetProcessDefinitionName: 'Order Process v2',
          mappingInstructions: [
            {
              sourceElementId: 'Activity_106kosb',
              sourceElementName: 'Take Pizza',
              targetElementId: 'Activity_106kosb',
              targetElementName: 'Take Pizza',
            },
            {
              sourceElementId: 'Activity_116kosb',
              sourceElementName: 'Deliver Pizza',
              targetElementId: 'Activity_126kosb',
              targetElementName: 'Deliver Pizza with drone',
            },
          ],
        },
      },
    },
    {
      id: '2',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'MODIFY_PROCESS_INSTANCE',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 60000).toISOString(),
      user: 'Jane Smith',
      details: {
        modifications: {
          activateInstructions: [
            {
              elementId: 'Activity_106kosb',
              elementName: 'Take Pizza',
              variableInstructions: [
                {
                  variables: {},
                  scopeId: '',
                },
              ],
              ancestorElementInstanceKey: '-1',
            },
          ],
          terminateInstructions: [
            {
              elementInstanceKey: '2251799813686789',
              elementName: 'Deliver Pizza',
            },
          ],
        },
      },
    },
    {
      id: '1',
      processDefinitionName: 'Order Process',
      processDefinitionVersion: 1,
      processInstanceKey: '123',
      tenantId: 'default',
      operationType: 'RESOLVE_INCIDENT',
      operationState: 'COMPLETED',
      startTimestamp: new Date(Date.now() - 65000).toISOString(),
      user: 'John Doe',
      comment: 'Resolved the incident by updating the variable.',
    },
];