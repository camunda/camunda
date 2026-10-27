/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {
  useAppendableElements,
  useCancellableElements,
  useModifiableElements,
  useNonModifiableElements,
} from './processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';
import {multiInstanceProcess} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';

function getWrapper() {
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="process-1">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[Paths.processInstance('instance-1')]}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
}

describe('processInstanceDetailsDiagram hooks', () => {
  beforeEach(() => {
    modificationsStore.reset();
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
  });

  describe('useCancellableElements', () => {
    it('returns node IDs where active > 0', async () => {
      const statistics: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'filterTask',
            active: 2,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
          {
            elementId: 'mapTask',
            active: 0,
            completed: 1,
            canceled: 0,
            incidents: 0,
          },
        ],
      };
      mockFetchElementInstancesStatistics().withSuccess(statistics);
      mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

      const {result} = renderHook(() => useCancellableElements(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => expect(result.current).toContain('filterTask'));
      expect(result.current).not.toContain('mapTask');
      expect(result.current).toHaveLength(1);
    });

    it('returns node IDs where incidents > 0', async () => {
      const statistics: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'filterTask',
            active: 0,
            completed: 0,
            canceled: 0,
            incidents: 1,
          },
          {
            elementId: 'mapTask',
            active: 0,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
        ],
      };
      mockFetchElementInstancesStatistics().withSuccess(statistics);
      mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

      const {result} = renderHook(() => useCancellableElements(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => expect(result.current).toContain('filterTask'));
      expect(result.current).not.toContain('mapTask');
      expect(result.current).toHaveLength(1);
    });
  });

  describe('useAppendableElements', () => {
    describe('add-token mode', () => {
      it('includes MI-inner nodes when the MI subprocess has exactly 1 running instance', async () => {
        const statistics: GetProcessInstanceStatisticsResponseBody = {
          items: [
            {
              elementId: 'filterMapSubProcess',
              active: 1,
              completed: 0,
              canceled: 0,
              incidents: 0,
            },
          ],
        };
        mockFetchElementInstancesStatistics().withSuccess(statistics);
        mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

        const {result} = renderHook(() => useAppendableElements(), {
          wrapper: getWrapper(),
        });

        await waitFor(() => expect(result.current).toContain('filterTask'));
        expect(result.current).toContain('mapTask');
      });

      it('excludes MI-inner nodes when the MI subprocess has more than 1 running instance', async () => {
        const statistics: GetProcessInstanceStatisticsResponseBody = {
          items: [
            {
              elementId: 'filterMapSubProcess',
              active: 2,
              completed: 0,
              canceled: 0,
              incidents: 0,
            },
          ],
        };
        mockFetchElementInstancesStatistics().withSuccess(statistics);
        mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

        const {result} = renderHook(() => useAppendableElements(), {
          wrapper: getWrapper(),
        });

        await waitFor(() => {
          expect(result.current).not.toContain('filterTask');
          expect(result.current).not.toContain('mapTask');
        });
      });
    });

    describe('moving-token mode', () => {
      it('includes MI-inner nodes with a single scope regardless of move-all flag', async () => {
        const statistics: GetProcessInstanceStatisticsResponseBody = {
          items: [
            {
              elementId: 'filterMapSubProcess',
              active: 1,
              completed: 0,
              canceled: 0,
              incidents: 0,
            },
          ],
        };
        mockFetchElementInstancesStatistics().withSuccess(statistics);
        mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

        modificationsStore.startMovingToken('peterFork', 'instance-key-1');

        const {result} = renderHook(() => useAppendableElements(), {
          wrapper: getWrapper(),
        });

        await waitFor(() => expect(result.current).toContain('filterTask'));
        expect(result.current).toContain('mapTask');
      });

      it('excludes MI-inner nodes with multiple scopes when isMoveAllOperation is true (no instance key)', async () => {
        const statistics: GetProcessInstanceStatisticsResponseBody = {
          items: [
            {
              elementId: 'filterMapSubProcess',
              active: 2,
              completed: 0,
              canceled: 0,
              incidents: 0,
            },
          ],
        };
        mockFetchElementInstancesStatistics().withSuccess(statistics);
        mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

        modificationsStore.startMovingToken('peterFork');

        const {result} = renderHook(() => useAppendableElements(), {
          wrapper: getWrapper(),
        });

        await waitFor(() => {
          expect(result.current).not.toContain('filterTask');
          expect(result.current).not.toContain('mapTask');
        });
      });

      it('includes MI-inner nodes with multiple scopes when source is from the same MI parent', async () => {
        const statistics: GetProcessInstanceStatisticsResponseBody = {
          items: [
            {
              elementId: 'filterMapSubProcess',
              active: 2,
              completed: 0,
              canceled: 0,
              incidents: 0,
            },
          ],
        };
        mockFetchElementInstancesStatistics().withSuccess(statistics);
        mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

        modificationsStore.startMovingToken('filterTask', 'instance-key-1');

        const {result} = renderHook(() => useAppendableElements(), {
          wrapper: getWrapper(),
        });

        await waitFor(() => {
          expect(result.current).toContain('mapTask');
        });
      });

      it('excludes MI-inner nodes with multiple scopes when source is from a different MI parent', async () => {
        const statistics: GetProcessInstanceStatisticsResponseBody = {
          items: [
            {
              elementId: 'filterMapSubProcess',
              active: 2,
              completed: 0,
              canceled: 0,
              incidents: 0,
            },
          ],
        };
        mockFetchElementInstancesStatistics().withSuccess(statistics);
        mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

        modificationsStore.startMovingToken('peterFork', 'instance-key-1');

        const {result} = renderHook(() => useAppendableElements(), {
          wrapper: getWrapper(),
        });

        await waitFor(() => {
          expect(result.current).not.toContain('filterTask');
          expect(result.current).not.toContain('mapTask');
        });
      });
    });
  });

  describe('useModifiableElements', () => {
    it('in moving-token mode, returns appendable nodes excluding the source node', async () => {
      const statistics: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'filterMapSubProcess',
            active: 1,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
        ],
      };
      mockFetchElementInstancesStatistics().withSuccess(statistics);
      mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

      modificationsStore.startMovingToken('filterTask', 'instance-key-1');

      const {result} = renderHook(() => useModifiableElements(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => {
        expect(result.current).not.toContain('filterTask');
        expect(result.current).toContain('mapTask');
      });
    });

    it('in default mode, returns the union of appendable and cancellable nodes without duplicates', async () => {
      const statistics: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'filterMapSubProcess',
            active: 2,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
          {
            elementId: 'filterTask',
            active: 0,
            completed: 0,
            canceled: 0,
            incidents: 1,
          },
        ],
      };
      mockFetchElementInstancesStatistics().withSuccess(statistics);
      mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

      const {result} = renderHook(() => useModifiableElements(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => {
        expect(result.current).toContain('filterTask');
        expect(
          result.current.filter((id) => id === 'filterMapSubProcess'),
        ).toHaveLength(1);
      });
    });
  });

  describe('useNonModifiableElements', () => {
    it('includes MI-inner nodes that are neither appendable nor cancellable', async () => {
      const statistics: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'filterMapSubProcess',
            active: 2,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
        ],
      };
      mockFetchElementInstancesStatistics().withSuccess(statistics);
      mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

      const {result} = renderHook(() => useNonModifiableElements(), {
        wrapper: getWrapper(),
      });

      await waitFor(() => expect(result.current).toContain('filterTask'));
      expect(result.current).toContain('mapTask');
    });

    it('excludes nodes that become modifiable once they have active instances', async () => {
      const statistics: GetProcessInstanceStatisticsResponseBody = {
        items: [
          {
            elementId: 'filterMapSubProcess',
            active: 1,
            completed: 0,
            canceled: 0,
            incidents: 0,
          },
        ],
      };
      mockFetchElementInstancesStatistics().withSuccess(statistics);
      mockFetchProcessDefinitionXml().withSuccess(multiInstanceProcess);

      const {result} = renderHook(() => useNonModifiableElements(), {
        wrapper: getWrapper(),
      });

      await waitFor(() =>
        expect(result.current).not.toContain('filterMapSubProcess'),
      );
      expect(result.current).not.toContain('filterTask');
      expect(result.current).not.toContain('mapTask');
    });
  });
});
