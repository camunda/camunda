/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import type {
  ElementInstance,
  QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {waitFor} from '@testing-library/react';

const mockProcessInstanceKey = '2251799813685625';
const mockChildScopeKey1 = '2251799813685630';
const mockChildScopeKey2 = '2251799813685640';
const mockGrandchildScopeKey = '2251799813685650';

const createMockElementInstance = (
  overrides: Partial<ElementInstance> = {},
): ElementInstance => ({
  elementInstanceKey: '2251799813685630',
  elementId: 'task_1',
  elementName: 'Task 1',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2023-01-01T10:00:00.000Z',
  processDefinitionKey: '2251799813685623',
  processDefinitionId: 'test-process',
  processInstanceKey: mockProcessInstanceKey,
  hasIncident: false,
  tenantId: '<default>',
  ...overrides,
});

const createMockResponse = (
  items: ElementInstance[],
  totalItems: number,
): QueryElementInstancesResponseBody => ({
  items,
  page: {
    totalItems,
  },
});

const mockFirstPageItems: ElementInstance[] = Array.from(
  {length: 100},
  (_, i) =>
    createMockElementInstance({
      elementInstanceKey: `${2251799813685630 + i}`,
      elementId: `task_${i}`,
      startDate: `2023-01-01T10:${String(i).padStart(2, '0')}:00.000Z`,
    }),
);

const mockFirstPageResponse = createMockResponse(mockFirstPageItems, 150);

const mockSecondPageItems: ElementInstance[] = Array.from(
  {length: 100},
  (_, i) =>
    createMockElementInstance({
      elementInstanceKey: `${2251799813685730 + i}`,
      elementId: `task_${i + 100}`,
      startDate: `2023-01-01T11:${String(i).padStart(2, '0')}:00.000Z`,
    }),
);

const mockSecondPageResponse = createMockResponse(mockSecondPageItems, 150);

const mockPreviousPageItems: ElementInstance[] = Array.from(
  {length: 50},
  (_, i) =>
    createMockElementInstance({
      elementInstanceKey: `${2251799813685530 + i}`,
      elementId: `task_prev_${i}`,
      startDate: `2023-01-01T09:${String(i).padStart(2, '0')}:00.000Z`,
    }),
);

const mockPreviousPageResponse = createMockResponse(mockPreviousPageItems, 150);

const mockChildInstances: ElementInstance[] = [
  createMockElementInstance({
    elementInstanceKey: mockChildScopeKey1,
    elementId: 'subprocess_1',
    elementName: 'Sub Process 1',
    type: 'SUB_PROCESS',
  }),
  createMockElementInstance({
    elementInstanceKey: mockChildScopeKey2,
    elementId: 'subprocess_2',
    elementName: 'Sub Process 2',
    type: 'SUB_PROCESS',
  }),
];

const mockChildResponse = createMockResponse(mockChildInstances, 2);

const mockEmptyResponse = createMockResponse([], 0);

describe('elementInstancesTreeStore', () => {
  afterEach(() => {
    elementInstancesTreeStore.reset();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('should initialize store with empty state', async () => {
    expect(elementInstancesTreeStore.state.rootScopeKey).toBe(null);
    expect(elementInstancesTreeStore.state.nodes.size).toBe(0);
    expect(elementInstancesTreeStore.state.expandedNodes.size).toBe(0);
  });

  it('should set root node and fetch first 2 pages automatically', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    expect(elementInstancesTreeStore.state.rootScopeKey).toBe(
      mockProcessInstanceKey,
    );
    expect(elementInstancesTreeStore.state.nodes.size).toBe(1);
    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockProcessInstanceKey),
    ).toBe(true);

    const items = elementInstancesTreeStore.getItems(mockProcessInstanceKey);
    expect(items.length).toBe(100);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.totalItems).toBe(150);
  });

  it('should mark root node as expanded after setting root', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockProcessInstanceKey),
    ).toBe(true);
  });

  it('should not reset state when setting same root node twice', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const firstCallItems = elementInstancesTreeStore.getItems(
      mockProcessInstanceKey,
    );

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    expect(elementInstancesTreeStore.state.nodes.size).toBe(1);

    const secondCallItems = elementInstancesTreeStore.getItems(
      mockProcessInstanceKey,
    );
    expect(secondCallItems).toBe(firstCallItems);
  });

  it('should expand node and fetch children when not already expanded', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockChildResponse);

    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(true);
    expect(elementInstancesTreeStore.state.nodes.has(mockChildScopeKey1)).toBe(
      true,
    );

    const items = elementInstancesTreeStore.getItems(mockChildScopeKey1);
    expect(items.length).toBe(2);
  });

  it('should not fetch again when expanding already expanded node', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockChildResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    const itemsBefore = elementInstancesTreeStore.getItems(mockChildScopeKey1);

    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    const itemsAfter = elementInstancesTreeStore.getItems(mockChildScopeKey1);
    expect(itemsAfter).toBe(itemsBefore);
  });

  it('should fetch 2 pages (100 items) when expanding node', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    const items = elementInstancesTreeStore.getItems(mockChildScopeKey1);
    expect(items.length).toBe(100);
  });

  it('should set loading status when expanding node', async () => {
    mockSearchElementInstances().withSuccess(mockChildResponse);

    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    const nodeData =
      elementInstancesTreeStore.state.nodes.get(mockChildScopeKey1);
    expect(nodeData?.status).toBe('loaded');
  });

  it('should set error status when expansion fails', async () => {
    mockSearchElementInstances().withServerError(500);

    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(true);

    const nodeData =
      elementInstancesTreeStore.state.nodes.get(mockChildScopeKey1);

    expect(nodeData).toEqual({
      items: [],
      pageMetadata: {
        totalItems: 0,
        windowStart: 0,
        windowEnd: 0,
      },
      status: 'error',
    });
  });

  it('should collapse node and remove from expandedNodes set', async () => {
    mockSearchElementInstances().withSuccess(mockEmptyResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    expect(elementInstancesTreeStore.isNodeExpanded(mockChildScopeKey1)).toBe(
      true,
    );

    elementInstancesTreeStore.collapseNode(mockChildScopeKey1);

    expect(elementInstancesTreeStore.isNodeExpanded(mockChildScopeKey1)).toBe(
      false,
    );
  });

  it('should remove collapsed node data from memory', async () => {
    mockSearchElementInstances().withSuccess(mockEmptyResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    expect(elementInstancesTreeStore.state.nodes.has(mockChildScopeKey1)).toBe(
      true,
    );

    elementInstancesTreeStore.collapseNode(mockChildScopeKey1);

    expect(elementInstancesTreeStore.getItems(mockChildScopeKey1)).toHaveLength(
      0,
    );
  });

  it('should recursively collapse all child nodes', async () => {
    const parentWithChildResponse = createMockResponse(
      [
        createMockElementInstance({
          elementInstanceKey: mockChildScopeKey1,
          elementId: 'child_scope',
        }),
      ],
      1,
    );

    const childWithGrandchildResponse = createMockResponse(
      [
        createMockElementInstance({
          elementInstanceKey: mockGrandchildScopeKey,
          elementId: 'grandchild_scope',
        }),
      ],
      1,
    );

    const grandchildResponse = createMockResponse([], 0);

    mockSearchElementInstances().withSuccess(parentWithChildResponse);
    await elementInstancesTreeStore.expandNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(childWithGrandchildResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    mockSearchElementInstances().withSuccess(grandchildResponse);
    await elementInstancesTreeStore.expandNode(mockGrandchildScopeKey);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockProcessInstanceKey),
    ).toBe(true);
    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(true);
    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockGrandchildScopeKey),
    ).toBe(true);

    elementInstancesTreeStore.collapseNode(mockProcessInstanceKey);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockProcessInstanceKey),
    ).toBe(false);
    expect(
      elementInstancesTreeStore.getItems(mockProcessInstanceKey),
    ).toHaveLength(0);
    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(false);
    expect(elementInstancesTreeStore.getItems(mockChildScopeKey1)).toHaveLength(
      0,
    );
    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockGrandchildScopeKey),
    ).toBe(false);
    expect(elementInstancesTreeStore.getItems(mockChildScopeKey1)).toHaveLength(
      0,
    );
  });

  it('should preserve sibling nodes when collapsing one branch', async () => {
    const parentWithTwoChildrenResponse = createMockResponse(
      [
        createMockElementInstance({
          elementInstanceKey: mockChildScopeKey1,
          elementId: 'child_scope_1',
        }),
        createMockElementInstance({
          elementInstanceKey: mockChildScopeKey2,
          elementId: 'child_scope_2',
        }),
      ],
      2,
    );

    mockSearchElementInstances().withSuccess(parentWithTwoChildrenResponse);
    await elementInstancesTreeStore.expandNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockEmptyResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    mockSearchElementInstances().withSuccess(mockEmptyResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey2);

    elementInstancesTreeStore.collapseNode(mockChildScopeKey1);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(false);
    expect(elementInstancesTreeStore.state.nodes.has(mockChildScopeKey1)).toBe(
      false,
    );

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey2),
    ).toBe(true);
    expect(elementInstancesTreeStore.state.nodes.has(mockChildScopeKey2)).toBe(
      true,
    );
  });

  it('should expand node when toggling collapsed node', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockChildResponse);

    await elementInstancesTreeStore.toggleNode(mockChildScopeKey1);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(true);
    expect(elementInstancesTreeStore.state.nodes.has(mockChildScopeKey1)).toBe(
      true,
    );
    expect(elementInstancesTreeStore.getItems(mockChildScopeKey1)).toHaveLength(
      2,
    );
  });

  it('should collapse node when toggling expanded node', async () => {
    mockSearchElementInstances().withSuccess(mockEmptyResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(true);

    elementInstancesTreeStore.toggleNode(mockChildScopeKey1);

    expect(
      elementInstancesTreeStore.state.expandedNodes.has(mockChildScopeKey1),
    ).toBe(false);
    expect(elementInstancesTreeStore.state.nodes.has(mockChildScopeKey1)).toBe(
      false,
    );
  });

  it('should fetch next page with correct offset and limit', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.windowStart).toBe(0);
    expect(nodeData?.pageMetadata.windowEnd).toBe(50);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);

    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    const updatedNodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(updatedNodeData?.pageMetadata.windowStart).toBe(50);
    expect(updatedNodeData?.pageMetadata.windowEnd).toBe(100);
  });

  it('should return count of new items when fetching next page', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);

    const count = await elementInstancesTreeStore.fetchNextPage(
      mockProcessInstanceKey,
    );

    expect(count).toBe(50);
  });

  it('should update window metadata after fetching next page', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);

    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.windowStart).toBe(50);
    expect(nodeData?.pageMetadata.windowEnd).toBe(100);
  });

  it('should replace items with new 2-page window', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const firstItems = elementInstancesTreeStore.getItems(
      mockProcessInstanceKey,
    );
    expect(firstItems.length).toBe(100);
    expect(firstItems[0].elementId).toBe('task_0');

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);

    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    const secondItems = elementInstancesTreeStore.getItems(
      mockProcessInstanceKey,
    );
    expect(secondItems.length).toBe(100);
    expect(secondItems[0].elementId).toBe('task_100');
  });

  it('should return 0 when reaching end of list', async () => {
    const endResponse = createMockResponse(mockFirstPageItems, 50);
    mockSearchElementInstances().withSuccess(endResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.windowEnd).toBe(50);
    expect(nodeData?.pageMetadata.totalItems).toBe(50);

    const count = await elementInstancesTreeStore.fetchNextPage(
      mockProcessInstanceKey,
    );

    expect(count).toBe(0);
  });

  it('should return -1 when next page fetch fails', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withServerError(500);

    const count = await elementInstancesTreeStore.fetchNextPage(
      mockProcessInstanceKey,
    );

    expect(count).toBe(-1);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.status).toBe('error');
  });

  it('should fetch previous page with correct offset and limit', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);
    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.windowStart).toBe(50);

    mockSearchElementInstances().withSuccess(mockPreviousPageResponse);

    await elementInstancesTreeStore.fetchPreviousPage(mockProcessInstanceKey);

    const updatedNodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(updatedNodeData?.pageMetadata.windowStart).toBe(0);
    expect(updatedNodeData?.pageMetadata.windowEnd).toBe(50);
  });

  it('should return count of new items when fetching previous page', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);
    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockPreviousPageResponse);

    const count = await elementInstancesTreeStore.fetchPreviousPage(
      mockProcessInstanceKey,
    );

    expect(count).toBe(50);
  });

  it('should update window metadata after fetching previous page', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);
    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.windowStart).toBe(50);
    expect(nodeData?.pageMetadata.windowEnd).toBe(100);

    mockSearchElementInstances().withSuccess(mockPreviousPageResponse);

    await elementInstancesTreeStore.fetchPreviousPage(mockProcessInstanceKey);

    const updatedNodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(updatedNodeData?.pageMetadata.windowStart).toBe(0);
    expect(updatedNodeData?.pageMetadata.windowEnd).toBe(50);
  });

  it('should return 0 when at beginning of list', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.pageMetadata.windowStart).toBe(0);

    const count = await elementInstancesTreeStore.fetchPreviousPage(
      mockProcessInstanceKey,
    );

    expect(count).toBe(0);
  });

  it('should return -1 when previous page fetch fails', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);
    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    mockSearchElementInstances().withServerError(500);

    const count = await elementInstancesTreeStore.fetchPreviousPage(
      mockProcessInstanceKey,
    );

    expect(count).toBe(-1);

    const nodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(nodeData?.status).toBe('error');
  });

  it('should get items for existing node', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const items = elementInstancesTreeStore.getItems(mockProcessInstanceKey);

    expect(items).toHaveLength(100);
    expect(items).toEqual(mockFirstPageItems);
  });

  it('should return empty array for non-existent node', async () => {
    const items = elementInstancesTreeStore.getItems('non-existent-key');

    expect(items).toHaveLength(0);
    expect(items).toEqual([]);
  });

  it('should reset all state when reset is called', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockChildResponse);
    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    expect(elementInstancesTreeStore.state.rootScopeKey).not.toBe(null);
    expect(elementInstancesTreeStore.state.nodes.size).toBeGreaterThan(0);
    expect(elementInstancesTreeStore.state.expandedNodes.size).toBeGreaterThan(
      0,
    );
    expect(elementInstancesTreeStore.state.abortControllers).toHaveLength(2);

    elementInstancesTreeStore.reset();

    expect(elementInstancesTreeStore.state.rootScopeKey).toBe(null);
    expect(elementInstancesTreeStore.state.nodes.size).toBe(0);
    expect(elementInstancesTreeStore.state.expandedNodes.size).toBe(0);
    expect(elementInstancesTreeStore.state.abortControllers.size).toBe(0);
  });

  it('aborts pending fetch when switching to different root node', async () => {
    mockSearchElementInstances().withDelay(mockFirstPageResponse);
    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    const firstController =
      elementInstancesTreeStore.state.abortControllers.get(
        mockProcessInstanceKey,
      );
    expect(firstController).toBeDefined();

    const secondRootKey = '9999999999999999';
    mockSearchElementInstances().withSuccess(mockChildResponse);
    await elementInstancesTreeStore.setRootNode(secondRootKey);

    await waitFor(() => expect(firstController?.signal.aborted).toBe(true));

    expect(
      elementInstancesTreeStore.state.nodes.has(mockProcessInstanceKey),
    ).toBe(false);

    expect(elementInstancesTreeStore.state.nodes.has(secondRootKey)).toBe(true);
  });

  it('aborts pending fetch when collapsing a node', async () => {
    mockSearchElementInstances().withDelay(mockFirstPageResponse);

    elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    const controller =
      elementInstancesTreeStore.state.abortControllers.get(mockChildScopeKey1);
    expect(controller).toBeDefined();
    expect(controller?.signal.aborted).toBe(false);

    elementInstancesTreeStore.collapseNode(mockChildScopeKey1);

    expect(controller?.signal.aborted).toBe(true);

    expect(
      elementInstancesTreeStore.state.abortControllers.has(mockChildScopeKey1),
    ).toBe(false);

    await waitFor(() =>
      expect(
        elementInstancesTreeStore.state.nodes.has(mockChildScopeKey1),
      ).toBe(false),
    );
  });

  it('should not have a next page for non-existent node', () => {
    expect(elementInstancesTreeStore.hasNextPage('non-existent-key')).toBe(
      false,
    );
  });

  it('should have a next page', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    expect(elementInstancesTreeStore.hasNextPage(mockProcessInstanceKey)).toBe(
      true,
    );
  });

  it('should not have a next page', async () => {
    const singlePageResponse = createMockResponse(
      mockFirstPageItems.slice(0, 50),
      50,
    );
    mockSearchElementInstances().withSuccess(singlePageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    expect(elementInstancesTreeStore.hasNextPage(mockProcessInstanceKey)).toBe(
      false,
    );
  });

  it('should not have a previous page for non-existent node', () => {
    expect(elementInstancesTreeStore.hasPreviousPage('non-existent-key')).toBe(
      false,
    );
  });

  it('should not have a previous page for node at start of list', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    expect(
      elementInstancesTreeStore.hasPreviousPage(mockProcessInstanceKey),
    ).toBe(false);
  });

  it('should have a previous page for node not at start of list', async () => {
    mockSearchElementInstances().withSuccess(mockFirstPageResponse);
    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey);

    mockSearchElementInstances().withSuccess(mockSecondPageResponse);
    await elementInstancesTreeStore.fetchNextPage(mockProcessInstanceKey);

    expect(
      elementInstancesTreeStore.hasPreviousPage(mockProcessInstanceKey),
    ).toBe(true);
  });

  it('should start polling when setting root node', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: true,
    });

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    mockSearchElementInstances().withSuccess(
      createMockResponse(mockFirstPageItems, 160),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(elementInstancesTreeStore.isPollRequestRunning).toBe(false);
    });

    const updatedNodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(updatedNodeData?.pageMetadata.totalItems).toBe(160);

    vi.useRealTimers();
  });

  it('should not start polling with disabled polling', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: false,
    });

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    mockSearchElementInstances().withSuccess(
      createMockResponse(mockFirstPageItems, 160),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    vi.useRealTimers();
  });

  it('should start polling when enabling polling', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: false,
    });

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: true,
    });

    mockSearchElementInstances().withSuccess(
      createMockResponse(mockFirstPageItems, 160),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(elementInstancesTreeStore.isPollRequestRunning).toBe(false);
    });

    const updatedNodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(updatedNodeData?.pageMetadata.totalItems).toBe(160);

    vi.useRealTimers();
  });

  it('should stop polling when same root is set with enablePolling changing from true to false', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: true,
    });

    mockSearchElementInstances().withSuccess(
      createMockResponse(mockFirstPageItems, 160),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(elementInstancesTreeStore.isPollRequestRunning).toBe(false);
    });

    expect(
      elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
        ?.pageMetadata.totalItems,
    ).toBe(160);

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: false,
    });

    mockSearchElementInstances().withSuccess(
      createMockResponse(mockFirstPageItems, 170),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(160);
    });

    vi.useRealTimers();
  });

  it('should skip polling when document is hidden', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: true,
    });

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    vi.stubGlobal('visibilityState', 'hidden');

    mockSearchElementInstances().withSuccess(
      createMockResponse(mockFirstPageItems, 160),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });

    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('should update node data after successful poll', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchElementInstances().withSuccess(mockFirstPageResponse);

    elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: true,
    });

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.pageMetadata.totalItems,
      ).toBe(150);
    });
    expect(
      elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)?.items,
    ).toHaveLength(100);

    const updatedItems: ElementInstance[] = [
      ...mockFirstPageItems,
      createMockElementInstance({
        elementInstanceKey: '9999999999999999',
        elementId: 'new_task',
        startDate: '2023-01-01T12:00:00.000Z',
      }),
    ];
    mockSearchElementInstances().withSuccess(
      createMockResponse(updatedItems, 151),
    );

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(elementInstancesTreeStore.isPollRequestRunning).toBe(false);
    });

    const updatedNodeData = elementInstancesTreeStore.state.nodes.get(
      mockProcessInstanceKey,
    );
    expect(updatedNodeData?.pageMetadata.totalItems).toBe(151);
    expect(updatedNodeData?.items).toHaveLength(101);
    expect(updatedNodeData?.items[100].elementId).toBe('new_task');

    vi.useRealTimers();
  });
});
