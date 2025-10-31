/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';

// Operation Types
export type OperationType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_PROCESS_INSTANCE'
  | 'MIGRATE_PROCESS_INSTANCE'
  | 'MODIFY_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_DEFINITION'
  | 'ADD_VARIABLE'
  | 'UPDATE_VARIABLE'
  | 'DELETE_DECISION_DEFINITION'
  | 'COMPLETE_USER_TASK'
  | 'ASSIGN_USER_TASK';

// Operation States
export type OperationState =
  | 'CREATED'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

// Process Instance States
export type ProcessInstanceState =
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELED'
  | 'INCIDENT'
  | 'TERMINATED';

// Audit Log Entry
export type AuditLogEntry = {
  id: string;
  processDefinitionName: string;
  processDefinitionVersion: number;
  processInstanceKey?: string;
  processInstanceState?: ProcessInstanceState;
  tenantId: string;
  operationType: OperationType;
  operationState: OperationState;
  startTimestamp: string;
  endTimestamp?: string;
  user: string;
  comment?: string;
};

// Search Filters
export type AuditLogSearchFilters = {
  processDefinitionName?: string;
  processDefinitionVersion?: number;
  processInstanceKey?: string;
  processInstanceState?: ProcessInstanceState;
  tenantId?: string;
  operationType?: OperationType;
  operationState?: OperationState;
  startDateFrom?: string;
  startDateTo?: string;
  endDateFrom?: string;
  endDateTo?: string;
  user?: string;
  comment?: string;
  searchQuery?: string;
};

// Sorting
export type SortField =
  | 'processDefinitionName'
  | 'operationType'
  | 'operationState'
  | 'startTimestamp'
  | 'user';

export type SortOrder = 'ASC' | 'DESC';

// API Request Body Structure
export type AuditLogSearchRequest = {
  sort?: Array<{
    field: SortField;
    order: SortOrder;
  }>;
  filter?: AuditLogSearchFilters;
  page?: {
    from: number;
    limit: number;
  };
};

// Search Response
export type AuditLogSearchResponse = {
  items: AuditLogEntry[];
  totalCount: number;
};

const searchAuditLog = async (request: AuditLogSearchRequest) => {
  return requestWithThrow<AuditLogSearchResponse>({
    url: `/api/v2/audit-log/search`,
    method: 'POST',
    body: request,
  });
};

export {searchAuditLog};
