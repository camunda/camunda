/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {renderHook} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {useModificationsByFlowNode} from './modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';

describe('modifications hooks', () => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    useEffect(() => {
      return () => {
        processInstanceDetailsDiagramStore.reset();
        modificationsStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('useModificationsByFlowNode', () => {
    it('should return modifications by flow node', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {id: 'node1', name: 'node1'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
          scopeId: 'scope1',
          parentScopeIds: {},
        },
      });

      const {result} = renderHook(() => useModificationsByFlowNode(), {
        wrapper: Wrapper,
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

    it('should handle CANCEL_TOKEN operations', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'CANCEL_TOKEN',
          flowNode: {id: 'node1', name: 'node1'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
        },
      });

      const {result} = renderHook(() => useModificationsByFlowNode(), {
        wrapper: Wrapper,
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

    it('should handle MOVE_TOKEN operations', () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          flowNode: {id: 'node1', name: 'node1'},
          targetFlowNode: {id: 'node2', name: 'node2'},
          affectedTokenCount: 5,
          visibleAffectedTokenCount: 3,
          scopeIds: [],
          parentScopeIds: {},
        },
      });

      const {result} = renderHook(() => useModificationsByFlowNode(), {
        wrapper: Wrapper,
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
});
