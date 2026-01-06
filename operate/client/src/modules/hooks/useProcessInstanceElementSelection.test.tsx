/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor, act} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes, useLocation} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: 'service-task-1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionId: 'process-def-1',
  processInstanceKey: '123',
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const mockElementInstance2: ElementInstance = {
  elementInstanceKey: '2251799813699890',
  elementId: 'service-task-2',
  elementName: 'Service Task 2',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2018-06-21',
  endDate: '2018-06-22',
  processDefinitionId: 'process-def-1',
  processInstanceKey: '123',
  processDefinitionKey: '2',
  hasIncident: false,
  tenantId: '<default>',
};

const getWrapper = (initialSearchParams?: {[key: string]: string}) => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    const searchParams = new URLSearchParams(initialSearchParams);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter
          initialEntries={[
            `${Paths.processInstance('123')}?${searchParams.toString()}`,
          ]}
        >
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
  return Wrapper;
};

describe('useProcessInstanceElementSelection', () => {
  describe('Initial state', () => {
    it('should return null for resolvedElementInstance when no selection', () => {
      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper(),
      });

      expect(result.current.resolvedElementInstance).toBeNull();
      expect(result.current.selectedElementId).toBeNull();
      expect(result.current.isFetchingElement).toBe(false);
    });
  });

  describe('selectElement', () => {
    it('should update URL with elementId only', () => {
      mockSearchElementInstances().withSuccess({
        items: [mockElementInstance],
        page: {totalItems: 1},
      });

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper(),
        },
      );

      act(() => {
        result.current.selectElement({elementId: 'service-task-1'});
      });

      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.location.search).toContain(
        'elementId=service-task-1',
      );
      expect(result.current.location.search).not.toContain(
        'elementInstanceKey',
      );
    });

    it('should remove elementInstanceKey from URL when called after selectElementInstance', () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );
      mockSearchElementInstances().withSuccess({
        items: [mockElementInstance],
        page: {totalItems: 1},
      });

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper(),
        },
      );

      act(() => {
        result.current.selectElementInstance({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
        });
      });

      expect(result.current.location.search).toContain(
        'elementInstanceKey=2251799813699889',
      );

      act(() => {
        result.current.selectElement({elementId: 'service-task-2'});
      });

      expect(result.current.selectedElementId).toBe('service-task-2');
      expect(result.current.location.search).toContain(
        'elementId=service-task-2',
      );
      expect(result.current.location.search).not.toContain(
        'elementInstanceKey',
      );
    });

    it('should update URL with isMultiInstanceBody when provided', () => {
      mockSearchElementInstances().withSuccess({
        items: [mockElementInstance],
        page: {totalItems: 1},
      });

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper(),
        },
      );

      act(() => {
        result.current.selectElement({
          elementId: 'service-task-1',
          isMultiInstanceBody: true,
        });
      });

      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.location.search).toContain(
        'elementId=service-task-1',
      );
      expect(result.current.location.search).toContain(
        'isMultiInstanceBody=true',
      );
    });

    it('should not include isMultiInstanceBody in URL when false', () => {
      mockSearchElementInstances().withSuccess({
        items: [mockElementInstance],
        page: {totalItems: 1},
      });

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper(),
        },
      );

      act(() => {
        result.current.selectElement({
          elementId: 'service-task-1',
          isMultiInstanceBody: false,
        });
      });

      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.location.search).toContain(
        'elementId=service-task-1',
      );
      expect(result.current.location.search).not.toContain(
        'isMultiInstanceBody',
      );
    });

    it('should fetch element instances when only elementId is provided', async () => {
      mockSearchElementInstances().withSuccess({
        items: [mockElementInstance],
        page: {totalItems: 1},
      });

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({elementId: 'service-task-1'}),
      });

      await waitFor(() =>
        expect(result.current.resolvedElementInstance).toEqual(
          mockElementInstance,
        ),
      );
      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.isFetchingElement).toBe(false);
    });

    it('should return null when search returns multiple element instances', async () => {
      mockSearchElementInstances().withSuccess({
        items: [mockElementInstance, mockElementInstance2],
        page: {totalItems: 2},
      });

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({elementId: 'service-task-1'}),
      });

      await waitFor(() => expect(result.current.isFetchingElement).toBe(false));

      expect(result.current.resolvedElementInstance).toBeNull();
    });

    it('should return null when search returns no element instances', async () => {
      mockSearchElementInstances().withSuccess({
        items: [],
        page: {totalItems: 0},
      });

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({elementId: 'service-task-1'}),
      });

      await waitFor(() => expect(result.current.isFetchingElement).toBe(false));

      expect(result.current.resolvedElementInstance).toBeNull();
    });

    it('should handle network error when searching element instances', async () => {
      mockSearchElementInstances().withNetworkError();

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({elementId: 'service-task-1'}),
      });

      await waitFor(() => expect(result.current.isFetchingElement).toBe(false));

      expect(result.current.resolvedElementInstance).toBeNull();
    });
  });

  describe('selectElementInstance', () => {
    it('should update URL with both elementId and elementInstanceKey', () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper(),
        },
      );

      act(() => {
        result.current.selectElementInstance({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
        });
      });

      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.location.search).toContain(
        'elementId=service-task-1',
      );
      expect(result.current.location.search).toContain(
        'elementInstanceKey=2251799813699889',
      );
    });

    it('should update URL with isMultiInstanceBody when provided', () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper(),
        },
      );

      act(() => {
        result.current.selectElementInstance({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
          isMultiInstanceBody: true,
        });
      });

      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.location.search).toContain(
        'elementId=service-task-1',
      );
      expect(result.current.location.search).toContain(
        'elementInstanceKey=2251799813699889',
      );
      expect(result.current.location.search).toContain(
        'isMultiInstanceBody=true',
      );
    });

    it('should fetch element instance by key when elementInstanceKey is provided', async () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
        }),
      });

      await waitFor(() =>
        expect(result.current.resolvedElementInstance).toEqual(
          mockElementInstance,
        ),
      );
      expect(result.current.selectedElementId).toBe('service-task-1');
      expect(result.current.isFetchingElement).toBe(false);
    });

    it('should handle network error when fetching element instance by key', async () => {
      mockFetchElementInstance('2251799813699889').withNetworkError();

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
        }),
      });

      await waitFor(() => expect(result.current.isFetchingElement).toBe(false));

      expect(result.current.resolvedElementInstance).toBeNull();
    });

    it('should prefer elementInstanceKey over elementId search', async () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
        }),
      });

      await waitFor(() =>
        expect(result.current.resolvedElementInstance).toEqual(
          mockElementInstance,
        ),
      );
    });
  });

  describe('clearSelection', () => {
    it('should remove elementId and elementInstanceKey from URL', () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );

      const {result} = renderHook(
        () => {
          const location = useLocation();
          const hook = useProcessInstanceElementSelection();
          return {...hook, location};
        },
        {
          wrapper: getWrapper({
            elementId: 'service-task-1',
            elementInstanceKey: '2251799813699889',
          }),
        },
      );

      act(() => {
        result.current.clearSelection();
      });

      expect(result.current.selectedElementId).toBeNull();
      expect(result.current.location.search).not.toContain('elementId');
      expect(result.current.location.search).not.toContain(
        'elementInstanceKey',
      );
    });

    it('should clear resolvedElementInstance when clearing selection', async () => {
      mockFetchElementInstance('2251799813699889').withSuccess(
        mockElementInstance,
      );

      const {result} = renderHook(() => useProcessInstanceElementSelection(), {
        wrapper: getWrapper({
          elementId: 'service-task-1',
          elementInstanceKey: '2251799813699889',
        }),
      });

      await waitFor(() =>
        expect(result.current.resolvedElementInstance).toEqual(
          mockElementInstance,
        ),
      );

      act(() => {
        result.current.clearSelection();
      });

      await waitFor(() =>
        expect(result.current.resolvedElementInstance).toBeNull(),
      );
    });
  });
});
