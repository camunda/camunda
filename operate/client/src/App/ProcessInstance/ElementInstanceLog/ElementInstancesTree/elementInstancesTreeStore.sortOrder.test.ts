/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import {mockServer} from 'modules/mock-server/node';
import {http, HttpResponse} from 'msw';
import {
  endpoints,
  type ElementInstance,
  type QueryElementInstancesRequestBody,
  type QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {waitFor} from '@testing-library/react';

const mockProcessInstanceKey = '2251799813685625';
const mockChildScopeKey = '2251799813685630';
const mockGrandchildKey = '2251799813685650';

const createMockElementInstance = (
  overrides: Partial<ElementInstance> = {},
): ElementInstance => ({
  elementInstanceKey: mockChildScopeKey,
  elementId: 'subprocess_1',
  elementName: 'Sub Process 1',
  type: 'SUB_PROCESS',
  state: 'ACTIVE',
  startDate: '2023-01-01T10:00:00.000Z',
  processDefinitionKey: '2251799813685623',
  processDefinitionId: 'test-process',
  processInstanceKey: mockProcessInstanceKey,
  hasIncident: false,
  tenantId: '<default>',
  endDate: null,
  rootProcessInstanceKey: null,
  incidentKey: null,
  ...overrides,
});

const createMockResponse = (
  items: ElementInstance[],
): QueryElementInstancesResponseBody => ({
  items,
  page: {
    totalItems: items.length,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
});

const rootChild = createMockElementInstance();
const grandchild = createMockElementInstance({
  elementInstanceKey: mockGrandchildKey,
  elementId: 'task_1',
  elementName: 'Task 1',
  type: 'SERVICE_TASK',
});

describe('elementInstancesTreeStore - sortOrder changes', () => {
  let requestedSortByScope: Record<
    string,
    QueryElementInstancesRequestBody['sort']
  >;

  beforeEach(() => {
    requestedSortByScope = {};

    mockServer.use(
      http.post(endpoints.queryElementInstances.getUrl(), async ({request}) => {
        const body = (await request.json()) as QueryElementInstancesRequestBody;
        const scopeKey = body.filter?.elementInstanceScopeKey ?? 'unknown';
        requestedSortByScope[scopeKey] = body.sort;

        const items =
          scopeKey === mockProcessInstanceKey
            ? [rootChild]
            : scopeKey === mockChildScopeKey
              ? [grandchild]
              : [];

        return HttpResponse.json(createMockResponse(items));
      }),
    );
  });

  afterEach(() => {
    elementInstancesTreeStore.reset();
  });

  it('should keep an expanded scope expanded when only the sort order changes on the same root', async () => {
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'desc',
    });

    await elementInstancesTreeStore.expandNode(mockChildScopeKey);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.getItems(mockChildScopeKey),
      ).toHaveLength(1);
    });

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'asc',
    });

    expect(elementInstancesTreeStore.isNodeExpanded(mockChildScopeKey)).toBe(
      true,
    );
    await waitFor(() => {
      expect(
        elementInstancesTreeStore.getItems(mockChildScopeKey),
      ).toHaveLength(1);
    });
  });

  it('should refetch an already-expanded scope with the new sort order', async () => {
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'desc',
    });

    await elementInstancesTreeStore.expandNode(mockChildScopeKey);

    await waitFor(
      () => {
        expect(requestedSortByScope[mockChildScopeKey]).toEqual([
          {field: 'startDate', order: 'desc'},
          {field: 'elementInstanceKey', order: 'desc'},
        ]);
      },
      {timeout: 2000},
    );

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'asc',
    });

    await waitFor(
      () => {
        expect(requestedSortByScope[mockChildScopeKey]).toEqual([
          {field: 'startDate', order: 'asc'},
          {field: 'elementInstanceKey', order: 'asc'},
        ]);
      },
      {timeout: 2000},
    );
  });
});
