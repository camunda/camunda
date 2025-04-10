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
  useFlowNodes,
  useAppendableFlowNodes,
  useCancellableFlowNodes,
  useModifiableFlowNodes,
} from './processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

jest.mock('modules/stores/modifications', () => ({
  modificationsStore: {
    state: {
      status: 'idle',
      sourceFlowNodeIdForMoveOperation: null,
    },
  },
}));

jest.mock('modules/bpmn-js/utils/isMoveModificationTarget', () => ({
  isMoveModificationTarget: jest.fn(() => true),
}));

describe('processInstanceDetailsDiagram hooks', () => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          {children}
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  beforeEach(async () => {
    jest.clearAllMocks();

    jest
      .spyOn(
        require('modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics'),
        'useFlownodeInstancesStatistics',
      )
      .mockReturnValue({
        data: {
          items: [
            {flowNodeId: 'StartEvent_1', active: 5, incidents: 0},
            {flowNodeId: 'Activity_0qtp1k6', active: 0, incidents: 1},
          ],
        },
      });
  });

  describe('useFlowNodes', () => {
    it('should return flow nodes with their states', async () => {
      const {result} = renderHook(() => useFlowNodes(), {wrapper: Wrapper});

      await waitFor(() =>
        expect(result.current).toEqual([
          {
            id: 'StartEvent_1',
            isCancellable: true,
            hasMultipleScopes: false,
            isMoveModificationTarget: true,
          },
          {
            id: 'Activity_0qtp1k6',
            isCancellable: true,
            hasMultipleScopes: false,
            isMoveModificationTarget: true,
          },
          {
            id: 'Event_0bonl61',
            isCancellable: false,
            hasMultipleScopes: false,
            isMoveModificationTarget: true,
          },
        ]),
      );
    });
  });

  describe('useAppendableFlowNodes', () => {
    it('should return appendable flow nodes', async () => {
      (isMoveModificationTarget as jest.Mock).mockImplementation(
        (flowNode) => flowNode.id === 'StartEvent_1',
      );

      const {result} = renderHook(() => useAppendableFlowNodes(), {
        wrapper: Wrapper,
      });

      await waitFor(() => expect(result.current).toEqual(['StartEvent_1']));
    });
  });

  describe('useCancellableFlowNodes', () => {
    it('should return cancellable flow nodes', async () => {
      const {result} = renderHook(() => useCancellableFlowNodes(), {
        wrapper: Wrapper,
      });

      await waitFor(() =>
        expect(result.current).toEqual(['StartEvent_1', 'Activity_0qtp1k6']),
      );
    });
  });

  describe('useModifiableFlowNodes', () => {
    it('should return filtered modifiable flow nodes when status is moving-token', async () => {
      modificationsStore.state.status = 'moving-token';
      modificationsStore.state.sourceFlowNodeIdForMoveOperation =
        'Activity_0qtp1k6';

      const {result} = renderHook(() => useModifiableFlowNodes(), {
        wrapper: Wrapper,
      });

      await waitFor(() => expect(result.current).toEqual(['StartEvent_1']));
    });
  });
});
