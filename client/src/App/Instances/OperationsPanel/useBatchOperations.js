/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useReducer, useCallback} from 'react';

import {
  SUBSCRIPTION_TOPIC,
  LOADING_STATE,
  POLL_TOPICS,
} from 'modules/constants';
import useSubscription from 'modules/hooks/useSubscription';
import useDataManager from 'modules/hooks/useDataManager';
import {hasRunningBatchOperations} from './service';
import {sortOperations} from './sortOperations';

const pageSize = 20;
const ACTIONS = Object.freeze({
  LOAD: 'LOAD',
  PREPEND: 'PREPEND',
  INCREASE_PAGE: 'INCREASE_PAGE',
});
const INITIAL_STATE = {batchOperations: [], page: 1};
function reducer(state, action) {
  switch (action.type) {
    case ACTIONS.LOAD: {
      const batchOperations = [
        ...state.batchOperations,
        ...action.payload,
      ].reduce((accumulator, operation) => {
        accumulator[operation.id] = operation;
        return accumulator;
      }, {});

      return {
        ...state,
        batchOperations: sortOperations(Object.values(batchOperations)),
      };
    }
    case ACTIONS.PREPEND: {
      return {
        ...state,
        batchOperations: [action.payload, ...state.batchOperations],
      };
    }
    case ACTIONS.INCREASE_PAGE: {
      return {
        ...state,
        page: state.page + 1,
      };
    }
    default:
      throw new Error('Unexpected action type');
  }
}

/**
 * This hook initially fetches and stores the first 20 batch operations and subscribes to further updates.
 * When active batch operations are fetched, is starts polling until all fetched operations are finished.
 */
export default function useBatchOperations() {
  const [state, dispatch] = useReducer(reducer, INITIAL_STATE);
  const {subscribe} = useSubscription();
  const dataManager = useDataManager();

  const requestBatchOperations = useCallback(() => {
    dataManager.getBatchOperations({pageSize: pageSize * state.page});
  }, [dataManager, state.page]);

  const requestNextBatchOperations = useCallback(
    (searchAfter) => {
      dispatch({type: ACTIONS.INCREASE_PAGE});
      dataManager.getBatchOperations({
        pageSize,
        searchAfter,
      });
    },
    [dataManager]
  );

  // Subscribe to updates on batch operations
  const subscribeToOperations = useCallback(() => {
    const unsubscribeLoadBatchOperations = subscribe(
      SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS,
      LOADING_STATE.LOADED,
      (payload) => {
        dispatch({type: ACTIONS.LOAD, payload});
      }
    );
    const unsubscribeCreateBatchOperations = subscribe(
      SUBSCRIPTION_TOPIC.CREATE_BATCH_OPERATION,
      LOADING_STATE.LOADED,
      (payload) => {
        dispatch({type: ACTIONS.PREPEND, payload});
      }
    );
    const unsubscribeOperationApplied = subscribe(
      SUBSCRIPTION_TOPIC.OPERATION_APPLIED,
      LOADING_STATE.LOADED,
      (payload) => {
        dispatch({
          type: ACTIONS.PREPEND,
          payload,
        });
      }
    );
    return () => {
      unsubscribeLoadBatchOperations();
      unsubscribeCreateBatchOperations();
      unsubscribeOperationApplied();
      dataManager.poll.unregister(POLL_TOPICS.OPERATIONS);
    };
  }, [subscribe, dataManager.poll]);

  useEffect(subscribeToOperations, []);

  // Register to polling, when there are running operations
  useEffect(() => {
    if (hasRunningBatchOperations(state.batchOperations)) {
      dataManager.poll.register(POLL_TOPICS.OPERATIONS, requestBatchOperations);
    } else {
      dataManager.poll.unregister(POLL_TOPICS.OPERATIONS);
    }
  }, [dataManager, requestBatchOperations, state.batchOperations]);

  return {
    batchOperations: state.batchOperations,
    requestBatchOperations,
    requestNextBatchOperations,
  };
}
