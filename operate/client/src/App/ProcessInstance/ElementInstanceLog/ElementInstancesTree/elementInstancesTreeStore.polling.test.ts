/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {waitFor} from '@testing-library/react';
import {searchResult} from 'modules/testUtils';
import {
  createMockElementInstance,
  MOCK_PROCESS_INSTANCE_KEY as mockProcessInstanceKey,
} from './mocks';

const mockChildScopeKey = '2251799813685630';
const mockGrandchildScopeKey = '2251799813685650';

describe('elementInstancesTreeStore - polling', () => {
  afterEach(() => {
    elementInstancesTreeStore.reset();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should keep polling an expanded scope that is still active even when all its loaded children are completed', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const activeSubProcess = createMockElementInstance({
      elementInstanceKey: mockChildScopeKey,
      elementId: 'subprocess_1',
      elementName: 'Sub Process 1',
      type: 'SUB_PROCESS',
      state: 'ACTIVE',
    });

    mockSearchElementInstances().withSuccess(searchResult([activeSubProcess]));

    await elementInstancesTreeStore.setRootNode(mockProcessInstanceKey, {
      enablePolling: true,
    });

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockProcessInstanceKey)
          ?.items,
      ).toHaveLength(1);
    });

    const completedChild = createMockElementInstance({
      elementInstanceKey: mockGrandchildScopeKey,
      elementId: 'completed_task',
      state: 'COMPLETED',
    });

    mockSearchElementInstances().withSuccess(searchResult([completedChild]));

    await elementInstancesTreeStore.expandNode(mockChildScopeKey);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockChildScopeKey)?.items,
      ).toHaveLength(1);
    });

    const newChild = createMockElementInstance({
      elementInstanceKey: '2251799813690001',
      elementId: 'new_task',
      state: 'ACTIVE',
    });

    // One poll tick fires both scopes at once, and msw matches the most
    // recently registered handler first. Registered in reverse order so the
    // root scope (requested first) is answered by the second handler below and
    // the child scope by the first.
    mockSearchElementInstances().withSuccess(
      searchResult([completedChild, newChild]),
    );
    mockSearchElementInstances().withSuccess(searchResult([activeSubProcess]));

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockChildScopeKey)?.items,
      ).toHaveLength(2);
    });

    vi.useRealTimers();
  });
});
