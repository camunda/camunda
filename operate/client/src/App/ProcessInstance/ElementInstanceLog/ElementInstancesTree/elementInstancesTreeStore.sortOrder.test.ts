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
} from '@camunda/camunda-api-zod-schemas/8.10';
import {waitFor} from '@testing-library/react';
import {searchResult} from 'modules/testUtils';
import {
  createMockElementInstance,
  MOCK_PROCESS_INSTANCE_KEY as mockProcessInstanceKey,
} from './mocks';

const mockChildScopeKey = '2251799813685630';
const mockGrandchildKey = '2251799813685650';
const mockGreatGrandchildKey = '2251799813685670';

const rootChild = createMockElementInstance({
  elementInstanceKey: mockChildScopeKey,
  elementId: 'subprocess_1',
  elementName: 'Sub Process 1',
  type: 'SUB_PROCESS',
});
const grandchild = createMockElementInstance({
  elementInstanceKey: mockGrandchildKey,
  elementId: 'task_1',
  elementName: 'Task 1',
  type: 'SERVICE_TASK',
});
const greatGrandchild = createMockElementInstance({
  elementInstanceKey: mockGreatGrandchildKey,
  elementId: 'task_2',
  elementName: 'Task 2',
  type: 'SERVICE_TASK',
});

describe('elementInstancesTreeStore - sortOrder changes', () => {
  let requestedSortByScope: Record<
    string,
    QueryElementInstancesRequestBody['sort']
  >;

  // root > child > grandchild > great-grandchild, one instance per level.
  const itemsByScope: Record<string, ElementInstance[]> = {
    [mockProcessInstanceKey]: [rootChild],
    [mockChildScopeKey]: [grandchild],
    [mockGrandchildKey]: [greatGrandchild],
  };

  beforeEach(() => {
    requestedSortByScope = {};

    mockServer.use(
      http.post(endpoints.queryElementInstances.getUrl(), async ({request}) => {
        const body = (await request.json()) as QueryElementInstancesRequestBody;
        const scopeKey = body.filter?.elementInstanceScopeKey ?? 'unknown';
        requestedSortByScope[scopeKey] = body.sort;

        return HttpResponse.json(searchResult(itemsByScope[scopeKey] ?? []));
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

  it('should fall back to the default sort order after a reset', async () => {
    // given a store left in ascending order
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'asc',
    });

    await waitFor(() => {
      expect(requestedSortByScope[mockProcessInstanceKey]).toEqual([
        {field: 'startDate', order: 'asc'},
        {field: 'elementInstanceKey', order: 'asc'},
      ]);
    });

    // when the store is reset and a root is set without an explicit order
    elementInstancesTreeStore.reset();
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    // then the default latest-first order is used again
    await waitFor(() => {
      expect(requestedSortByScope[mockProcessInstanceKey]).toEqual([
        {field: 'startDate', order: 'desc'},
        {field: 'elementInstanceKey', order: 'desc'},
      ]);
    });
  });
  it('should apply a sort order change to scopes nested more than one level deep', async () => {
    // given a grandchild scope expanded under an already expanded child scope
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'desc',
    });

    await elementInstancesTreeStore.expandNode(mockChildScopeKey);
    await elementInstancesTreeStore.expandNode(mockGrandchildKey);

    await waitFor(() => {
      expect(requestedSortByScope[mockGrandchildKey]).toEqual([
        {field: 'startDate', order: 'desc'},
        {field: 'elementInstanceKey', order: 'desc'},
      ]);
    });

    // when the sort order changes
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      sortOrder: 'asc',
    });

    // then the deepest scope is refetched with the new order and stays expanded
    await waitFor(() => {
      expect(requestedSortByScope[mockGrandchildKey]).toEqual([
        {field: 'startDate', order: 'asc'},
        {field: 'elementInstanceKey', order: 'asc'},
      ]);
    });

    expect(elementInstancesTreeStore.isNodeExpanded(mockGrandchildKey)).toBe(
      true,
    );
    expect(elementInstancesTreeStore.getItems(mockGrandchildKey)).toEqual([
      greatGrandchild,
    ]);
  });

  it('should discard an in-flight response when the sort order changes before it resolves', async () => {
    // given a latest-first request that has not answered yet
    let releaseStaleResponse: () => void = () => {};
    const staleResponseReleased = new Promise<void>((resolve) => {
      releaseStaleResponse = resolve;
    });

    const staleChild = createMockElementInstance({
      elementInstanceKey: '2251799813685699',
      elementId: 'stale_task',
      elementName: 'Stale Task',
      type: 'SUB_PROCESS',
    });

    mockServer.use(
      http.post(endpoints.queryElementInstances.getUrl(), async ({request}) => {
        const body = (await request.json()) as QueryElementInstancesRequestBody;

        if (body.sort?.[0]?.order === 'desc') {
          await staleResponseReleased;
          return HttpResponse.json(searchResult([staleChild]));
        }

        return HttpResponse.json(searchResult([rootChild]));
      }),
    );

    // when the order is switched to oldest-first before that response lands
    const stalePending = elementInstancesTreeStore.setRootNode(
      mockProcessInstanceKey,
      {sortOrder: 'desc'},
    );
    const freshPending = elementInstancesTreeStore.setRootNode(
      mockProcessInstanceKey,
      {sortOrder: 'asc'},
    );

    // and the superseded response only lands afterwards
    await freshPending;

    expect(elementInstancesTreeStore.getItems(mockProcessInstanceKey)).toEqual([
      rootChild,
    ]);

    releaseStaleResponse();
    await stalePending;

    // then the superseded response never reaches the tree
    await waitFor(() => {
      expect(
        elementInstancesTreeStore.getItems(mockProcessInstanceKey),
      ).toEqual([rootChild]);
    });

    expect(
      elementInstancesTreeStore
        .getItems(mockProcessInstanceKey)
        .map(({elementInstanceKey}) => elementInstanceKey),
    ).not.toContain(staleChild.elementInstanceKey);
  });
});
