/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse, type RequestHandler} from 'msw';
import {mockAuditLogEntries} from 'modules/mocks/auditLog';
import type {
  AuditLogSearchResponse,
  AuditLogSearchRequest,
  AuditLogEntry,
} from 'modules/api/v2/auditLog/searchAuditLog';
import type {
  UpsertCommentRequest,
  UpsertCommentResponse,
} from 'modules/api/v2/auditLog/upsertComment';

// In-memory storage for comments
let auditLogData = [...mockAuditLogEntries];

const handlers: RequestHandler[] = [
  // Search audit log
  http.post('/api/v2/audit-log/search', async ({request}) => {
    const body = (await request.json()) as AuditLogSearchRequest;

    // Extract pagination params
    const from = body.page?.from ?? 0;
    const limit = body.page?.limit ?? 50;

    // Filter data
    let filteredData = [...auditLogData];
    const filter = body.filter || {};

    if (filter.searchQuery) {
      const query = filter.searchQuery.toLowerCase();
      filteredData = filteredData.filter(
        (entry) =>
          entry.processDefinitionName.toLowerCase().includes(query) ||
          entry.user.toLowerCase().includes(query) ||
          entry.comment?.toLowerCase().includes(query),
      );
    }

    if (filter.processDefinitionName) {
      filteredData = filteredData.filter((entry) =>
        entry.processDefinitionName
          .toLowerCase()
          .includes(filter.processDefinitionName!.toLowerCase()),
      );
    }

    if (filter.processDefinitionVersion) {
      filteredData = filteredData.filter(
        (entry) =>
          entry.processDefinitionVersion === filter.processDefinitionVersion,
      );
    }

    if (filter.processInstanceKey) {
      filteredData = filteredData.filter(
        (entry) => entry.processInstanceKey === filter.processInstanceKey,
      );
    }

    if (filter.processInstanceState) {
      filteredData = filteredData.filter(
        (entry) => entry.processInstanceState === filter.processInstanceState,
      );
    }

    if (filter.tenantId) {
      filteredData = filteredData.filter(
        (entry) => entry.tenantId === filter.tenantId,
      );
    }

    if (filter.operationType) {
      filteredData = filteredData.filter(
        (entry) => entry.operationType === filter.operationType,
      );
    }

    if (filter.operationState) {
      filteredData = filteredData.filter(
        (entry) => entry.operationState === filter.operationState,
      );
    }

    if (filter.user) {
      filteredData = filteredData.filter((entry) =>
        entry.user.toLowerCase().includes(filter.user!.toLowerCase()),
      );
    }

    if (filter.comment) {
      filteredData = filteredData.filter(
        (entry) =>
          entry.comment &&
          entry.comment.toLowerCase().includes(filter.comment!.toLowerCase()),
      );
    }

    // Apply sorting
    if (body.sort && body.sort.length > 0) {
      const sortConfig = body.sort[0];
      const sortOrder = sortConfig.order === 'DESC' ? -1 : 1;
      filteredData.sort((a, b) => {
        const aVal = a[sortConfig.field as keyof AuditLogEntry];
        const bVal = b[sortConfig.field as keyof AuditLogEntry];

        if (aVal === undefined || aVal === null) {
          return 1;
        }
        if (bVal === undefined || bVal === null) {
          return -1;
        }

        if (typeof aVal === 'string' && typeof bVal === 'string') {
          return aVal.localeCompare(bVal) * sortOrder;
        }

        if (typeof aVal === 'number' && typeof bVal === 'number') {
          return (aVal - bVal) * sortOrder;
        }

        return 0;
      });
    }

    // Apply pagination
    const paginatedData = filteredData.slice(from, from + limit);

    const response: AuditLogSearchResponse = {
      items: paginatedData,
      totalCount: filteredData.length,
    };

    return HttpResponse.json(response);
  }),

  // Upsert comment
  http.post('/api/v2/audit-log', async ({request}) => {
    const body = (await request.json()) as UpsertCommentRequest;

    const entryIndex = auditLogData.findIndex((entry) => entry.id === body.id);

    if (entryIndex !== -1) {
      auditLogData[entryIndex] = {
        ...auditLogData[entryIndex],
        comment: body.comment,
      };

      const response: UpsertCommentResponse = {
        id: body.id,
        comment: body.comment,
      };

      return HttpResponse.json(response);
    }

    return new HttpResponse(null, {status: 404});
  }),
];

export {handlers};
