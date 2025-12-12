/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export interface AuditLogEntry {
  id: string;
  processDefinition: string;
  operation: string;
  status: string;
  startTimestamp: Date;
  user: string;
  comment: string;
}

export const mockAuditLogData: AuditLogEntry[] = [
  {
    id: '1',
    processDefinition: 'A test process',
    operation: 'Migrate',
    status: 'In progress...',
    startTimestamp: new Date('2024-01-14T17:51:12Z'),
    user: 'Andy Bernard',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '2',
    processDefinition: 'Check in process',
    operation: 'Modify',
    status: 'Completed (2 success/2 fail)',
    startTimestamp: new Date('2024-01-14T14:41:23Z'),
    user: 'Dwight Schrute',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '3',
    processDefinition: 'Check in process',
    operation: 'Cancel',
    status: 'Completed (instance)',
    startTimestamp: new Date('2024-01-13T11:01:34Z'),
    user: 'Michael Scott',
    comment: '',
  },
  {
    id: '4',
    processDefinition: 'process d',
    operation: 'Retry',
    status: 'Completed (2 success)',
    startTimestamp: new Date('2024-01-13T09:51:45Z'),
    user: 'Ryan Howard',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '5',
    processDefinition: 'Process C',
    operation: 'Retry',
    status: 'Completed (1 fail)',
    startTimestamp: new Date('2024-01-12T13:33:57Z'),
    user: 'Toby Flenderson',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '6',
    processDefinition: 'another process',
    operation: 'Retry',
    status: 'Completed (1 fail)',
    startTimestamp: new Date('2024-01-12T13:33:57Z'),
    user: 'Kelly Kapoor',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '7',
    processDefinition: 'testing process',
    operation: 'Retry',
    status: 'Completed (1 fail)',
    startTimestamp: new Date('2024-01-12T13:33:57Z'),
    user: 'Toby Flenderson',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '8',
    processDefinition: 'Process C',
    operation: 'Migrate',
    status: 'Completed (264 success)',
    startTimestamp: new Date('2024-01-12T13:33:57Z'),
    user: 'Oscar Martinez',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '9',
    processDefinition: 'Check in process',
    operation: 'Retry',
    status: 'Completed (1 fail)',
    startTimestamp: new Date('2024-01-12T13:33:57Z'),
    user: 'Gabe Lewis',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
  {
    id: '10',
    processDefinition: 'Final process',
    operation: 'Cancel',
    status: 'Completed (1 fail)',
    startTimestamp: new Date('2024-01-12T13:33:57Z'),
    user: 'Meredith Palmer',
    comment: 'Lorem ipsum dolor sit lorem a amet, consectetur',
  },
];

export const mockProcessDefinitions = [
  'All processes',
  'A test process',
  'Check in process',
  'Order process',
  'Payment process',
  'Approval process',
];

export const mockVersions = ['All', 'Version 1', 'Version 2', 'Version 3'];

export const mockOperationTypes = [
  'Choose option(s)',
  'Migrate',
  'Modify',
  'Cancel',
  'Retry',
  'Delete',
];

export const mockOperationStatuses = [
  'Choose option(s)',
  'In progress...',
  'Completed (2 success/2 fail)',
  'Completed (instance)',
  'Completed (2 success)',
  'Completed (1 fail)',
  'Completed (1 success)',
];
