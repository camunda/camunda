/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {
  useModificationsByElement,
  useWillAllElementsBeCanceled,
  useNewScopeKeyForElement,
  useAvailableModifications,
} from './modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  mockProcessWithInputOutputMappingsXML,
  eventSubProcess,
} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';

const getWrapper = () => {
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        modificationsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('modifications hooks', () => {
  describe('useWillAllElementsBeCanceled', () => {
    beforeEach(() => {
      modificationsStore.reset();
      mockFetchProcessInstance().withSuccess(mockProcessInstance);
    });

    it('should return true if all elements are canceled', async () => {
      const mockData: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'node1',
            active: 0,
            completed: 0,
            canceled: 1,
            incidents: 0,
          },
          {
            elementId: 'node2',
            active: 0,
            completed: 0,
            canceled: 1,
            incidents: 0,
          },
        ],
      };

      mockFetchElementInstancesStatistics().withSuccess(mockData);
      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useWillAllElementsBeCanceled(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => {
        expect(result.current).toBe(true);
      });
    });

    it('should return false if there are ADD_TOKEN or MOVE_TOKEN modifications', async () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {id: 'node1', name: 'Node 1'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'scope1',
          parentScopeIds: {},
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useWillAllElementsBeCanceled(), {
        wrapper: getWrapper(),
      });

      expect(result.current).toBe(false);
    });

    it('should return false if there are active elements', async () => {
      const mockData: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'node1',
            active: 1,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
          {
            elementId: 'node2',
            active: 0,
            completed: 0,
            canceled: 1,
            incidents: 0,
          },
        ],
      };

      mockFetchElementInstancesStatistics().withSuccess(mockData);
      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useWillAllElementsBeCanceled(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => expect(result.current).toBe(false));
    });

    it('should return false if there are elements with incidents', async () => {
      const mockData: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'node1',
            active: 0,
            completed: 0,
            canceled: 0,
            incidents: 1,
          },
          {
            elementId: 'node2',
            active: 0,
            completed: 0,
            canceled: 1,
            incidents: 0,
          },
        ],
      };

      mockFetchElementInstancesStatistics().withSuccess(mockData);
      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useWillAllElementsBeCanceled(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => expect(result.current).toBe(false));
    });

    it('should return false if there are no statistics', async () => {
      mockFetchElementInstancesStatistics().withSuccess({items: []});
      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useWillAllElementsBeCanceled(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => expect(result.current).toBe(false));
    });
  });

  describe('useModificationsByElement', () => {
    beforeEach(() => {
      mockFetchProcessInstance().withSuccess(mockProcessInstance);
    });

    it('should return modifications by element', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {id: 'node1', name: 'node1'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
          scopeId: 'scope1',
          parentScopeIds: {},
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useModificationsByElement(), {
        wrapper: getWrapper(),
      });

      expect(result.current).toEqual({
        node1: {
          newTokens: 5,
          cancelledTokens: 0,
          cancelledChildTokens: 0,
          visibleCancelledTokens: 0,
          areAllTokensCanceled: false,
        },
      });
    });

    it('should handle CANCEL_TOKEN operations', async () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'CANCEL_TOKEN',
          element: {id: 'node1', name: 'node1'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useModificationsByElement(), {
        wrapper: getWrapper(),
      });

      expect(result.current).toEqual({
        node1: {
          newTokens: 0,
          cancelledTokens: 5,
          cancelledChildTokens: 0,
          visibleCancelledTokens: 3,
          areAllTokensCanceled: false,
        },
      });
    });

    it('should handle MOVE_TOKEN operations', async () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          element: {id: 'node1', name: 'node1'},
          targetElement: {id: 'node2', name: 'node2'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
          scopeIds: [],
          parentScopeIds: {},
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useModificationsByElement(), {
        wrapper: getWrapper(),
      });

      expect(result.current).toEqual({
        node1: {
          newTokens: 0,
          cancelledTokens: 5,
          cancelledChildTokens: 0,
          visibleCancelledTokens: 3,
          areAllTokensCanceled: false,
        },
        node2: {
          newTokens: 5,
          cancelledTokens: 0,
          cancelledChildTokens: 0,
          visibleCancelledTokens: 0,
          areAllTokensCanceled: false,
        },
      });
    });
  });

  describe('useNewScopeKeyForElement', () => {
    beforeEach(() => {
      mockFetchProcessInstance().withSuccess(mockProcessInstance);
    });

    it('should return null if elementId is null', () => {
      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useNewScopeKeyForElement(null), {
        wrapper: getWrapper(),
      });

      expect(result.current).toBeNull();
    });

    it('should return null if no matching ADD_TOKEN modification exists', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'CANCEL_TOKEN',
          element: {id: 'node1', name: 'node1'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useNewScopeKeyForElement('node1'), {
        wrapper: getWrapper(),
      });

      expect(result.current).toBeNull();
    });

    it('should return the scopeId for an ADD_TOKEN modification', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          element: {id: 'node1', name: 'node1'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'scope1',
          parentScopeIds: {},
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useNewScopeKeyForElement('node1'), {
        wrapper: getWrapper(),
      });

      expect(result.current).toBe('scope1');
    });

    it('should return the first scopeId for a MOVE_TOKEN modification', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          element: {id: 'node1', name: 'node1'},
          targetElement: {id: 'node2', name: 'node2'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeIds: ['scope2', 'scope3'],
          parentScopeIds: {},
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useNewScopeKeyForElement('node2'), {
        wrapper: getWrapper(),
      });

      expect(result.current).toBe('scope2');
    });

    it('should return null if no matching MOVE_TOKEN modification exists', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          element: {id: 'node1', name: 'node1'},
          targetElement: {id: 'node2', name: 'node2'},
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeIds: [],
          parentScopeIds: {},
        },
      });

      mockFetchProcessDefinitionXml().withSuccess(
        mockProcessWithInputOutputMappingsXML,
      );

      const {result} = renderHook(() => useNewScopeKeyForElement('node2'), {
        wrapper: getWrapper(),
      });

      expect(result.current).toBeNull();
    });
  });

  describe('useAvailableModifications', () => {
    beforeEach(() => {
      mockFetchProcessInstance().withSuccess(mockProcessInstance);
      mockFetchProcessDefinitionXml().withSuccess(eventSubProcess);
      mockFetchElementInstancesStatistics().withSuccess({items: []});
    });

    afterEach(() => {
      modificationsStore.reset();
    });

    it('should return empty array if element cannot be modified', async () => {
      const {result: resultNonModifiable} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 1,
            elementId: 'StartEvent_1',
            isSingleRunningInstanceResolved: true,
          }),
        {wrapper: getWrapper()},
      );

      await waitFor(() => {
        expect(resultNonModifiable.current).toEqual([]);
      });
    });

    it('should return only add when no active instances in statistics', async () => {
      const {result} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 1,
            elementId: 'ServiceTask_1daop2o',
            isSingleRunningInstanceResolved: true,
          }),
        {wrapper: getWrapper()},
      );

      await waitFor(() => expect(result.current.length).toBeGreaterThan(0));
      expect(result.current).toEqual(['add']);
    });

    it('should return only add for subprocess when no active instances', async () => {
      const {result} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 1,
            elementId: 'ServiceTask_0ruokei',
            isSingleRunningInstanceResolved: true,
          }),
        {wrapper: getWrapper()},
      );

      await waitFor(() => expect(result.current.length).toBeGreaterThan(0));
      expect(result.current).toEqual(['add']);
    });

    it('should not include add option if elementInstanceKey is provided', () => {
      const {result} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 1,
            elementId: 'ServiceTask_1daop2o',
            isSpecificElementInstanceSelected: true,
            isSingleRunningInstanceResolved: true,
          }),
        {wrapper: getWrapper()},
      );

      expect(result.current).not.toContain('add');
    });

    it('should include add, cancel-instance, and move-instance with single active instance', async () => {
      mockFetchElementInstancesStatistics().withSuccess({
        items: [
          {
            elementId: 'ServiceTask_1daop2o',
            active: 1,
            incidents: 0,
            completed: 0,
            canceled: 0,
          },
        ],
      });

      const {result} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 1,
            elementId: 'ServiceTask_1daop2o',
            isSingleRunningInstanceResolved: true,
          }),
        {wrapper: getWrapper()},
      );

      await waitFor(() => expect(result.current.length).toBe(3));

      expect(result.current).toEqual([
        'add',
        'cancel-instance',
        'move-instance',
      ]);
    });

    it('should include add, cancel-all, and move-all with multiple active instances', async () => {
      mockFetchElementInstancesStatistics().withSuccess({
        items: [
          {
            elementId: 'ServiceTask_1daop2o',
            active: 5,
            incidents: 0,
            completed: 0,
            canceled: 0,
          },
        ],
      });

      const {result} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 5,
            elementId: 'ServiceTask_1daop2o',
          }),
        {wrapper: getWrapper()},
      );

      await waitFor(() => expect(result.current.length).toBe(3));
      expect(result.current).toEqual(['add', 'cancel-all', 'move-all']);
    });

    it('should not include move options for subprocess even with active instances', async () => {
      mockFetchElementInstancesStatistics().withSuccess({
        items: [
          {
            elementId: 'ServiceTask_0ruokei',
            active: 1,
            incidents: 0,
            completed: 0,
            canceled: 0,
          },
        ],
      });

      const {result} = renderHook(
        () =>
          useAvailableModifications({
            runningElementInstanceCount: 1,
            elementId: 'ServiceTask_0ruokei',
            isSingleRunningInstanceResolved: true,
          }),
        {wrapper: getWrapper()},
      );

      await waitFor(() => expect(result.current.length).toBe(2));
      expect(result.current).toEqual(['add', 'cancel-all']);
    });
  });
});
