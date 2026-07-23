/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {waitFor} from '@testing-library/react';
import {searchResult} from 'modules/testUtils';

const mockProcessInstanceKey = '2251799813685625';
const mockChildScopeKey1 = '2251799813685630';
const mockGrandchildScopeKey = '2251799813685650';

const createMockElementInstance = (
  overrides: Partial<ElementInstance> = {},
): ElementInstance => ({
  elementInstanceKey: mockChildScopeKey1,
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
  endDate: null,
  rootProcessInstanceKey: null,
  incidentKey: null,
  ...overrides,
});

describe('elementInstancesTreeStore - polling', () => {
  afterEach(() => {
    elementInstancesTreeStore.reset();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should keep polling an expanded scope that is still active even when all its loaded children are completed', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const activeSubProcess = createMockElementInstance({
      elementInstanceKey: mockChildScopeKey1,
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

    await elementInstancesTreeStore.expandNode(mockChildScopeKey1);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockChildScopeKey1)?.items,
      ).toHaveLength(1);
    });

    const newChild = createMockElementInstance({
      elementInstanceKey: '2251799813690001',
      elementId: 'new_task',
      state: 'ACTIVE',
    });

    mockSearchElementInstances().withSuccess(
      searchResult([completedChild, newChild]),
    );
    mockSearchElementInstances().withSuccess(searchResult([activeSubProcess]));

    vi.advanceTimersByTime(5000);

    await waitFor(() => {
      expect(
        elementInstancesTreeStore.state.nodes.get(mockChildScopeKey1)?.items,
      ).toHaveLength(2);
    });

    vi.useRealTimers();
  });
});
