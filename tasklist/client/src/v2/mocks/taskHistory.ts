/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type TaskHistoryOperation = {
  id: string;
  timestamp: string;
  operationType: 'COMPLETE' | 'ASSIGN' | 'UNASSIGN' | 'UPDATE';
  status: 'SUCCESS' | 'FAIL';
  actor: {
    type: 'USER' | 'CLIENT';
    name: string;
  };
  details?: {
    property?: string; // Legacy: single property
    properties?: Array<{
      property: string;
      oldValue?: string;
      newValue?: string;
    }>; // New: multiple properties
    oldValue?: string;
    newValue?: string;
    errorMessage?: string;
  };
};

export const taskHistory: TaskHistoryOperation[] = [
  {
    id: '1',
    timestamp: new Date(Date.now() - 1000 * 60 * 2).toISOString(), // 2 minutes ago
    operationType: 'COMPLETE',
    status: 'SUCCESS',
    actor: {
      type: 'USER',
      name: 'john.doe',
    },
  },
  {
    id: '2',
    timestamp: new Date(Date.now() - 1000 * 60 * 5).toISOString(), // 5 minutes ago
    operationType: 'UPDATE',
    status: 'SUCCESS',
    actor: {
      type: 'USER',
      name: 'john.doe',
    },
    details: {
      properties: [
        {
          property: 'Priority',
          oldValue: '50',
          newValue: '75',
        },
        {
      property: 'Due Date',
      oldValue: new Date(Date.now() + 1000 * 60 * 60 * 48).toISOString(),
          newValue: undefined, // Unset (no value)
        },
      ],
    },
  },
  {
    id: '3',
    timestamp: new Date(Date.now() - 1000 * 60 * 15).toISOString(), // 15 minutes ago
    operationType: 'ASSIGN',
    status: 'SUCCESS',
    actor: {
      type: 'USER',
      name: 'jane.smith',
    },
    details: {
      oldValue: 'jane.smith',
      newValue: 'john.doe',
    },
  },
  {
    id: '4',
    timestamp: new Date(Date.now() - 1000 * 60 * 30).toISOString(), // 30 minutes ago
    operationType: 'UNASSIGN',
    status: 'SUCCESS',
    actor: {
      type: 'USER',
      name: 'jane.smith',
    },
    details: {
      oldValue: 'john.doe',
    },
  },
  {
    id: '5',
    timestamp: new Date(Date.now() - 1000 * 60 * 60).toISOString(), // 1 hour ago
    operationType: 'ASSIGN',
    status: 'SUCCESS',
    actor: {
      type: 'USER',
      name: 'john.doe',
    },
    details: {
      oldValue: undefined,
      newValue: 'john.doe',
    },
  },
  {
    id: '6',
    timestamp: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(), // 2 hours ago
    operationType: 'ASSIGN',
    status: 'FAIL',
    actor: {
      type: 'USER',
      name: 'jane.smith',
    },
    details: {
      errorMessage: 'User does not have permission to assign this task',
    },
  },
];

