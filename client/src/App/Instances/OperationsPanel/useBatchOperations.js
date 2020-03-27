/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useReducer, useCallback} from 'react';

import {
  SUBSCRIPTION_TOPIC,
  LOADING_STATE,
  POLL_TOPICS
} from 'modules/constants';
import useSubscription from 'modules/hooks/useSubscription';
import useDataManager from 'modules/hooks/useDataManager';

import {hasRunningBatchOperations} from './service';

const ACTIONS = Object.freeze({
  LOAD: 'LOAD',
  PREPEND: 'PREPEND'
});
const INITIAL_STATE = [];
function reducer(state, action) {
  switch (action.type) {
    case ACTIONS.LOAD: {
      return action.payload;
    }
    case ACTIONS.PREPEND: {
      return [action.payload, ...state];
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
  const [batchOperations, dispatch] = useReducer(reducer, INITIAL_STATE);
  const {subscribe, unsubscribe} = useSubscription();
  const dataManager = useDataManager();

  const requestBatchOperations = useCallback(() => {
    dataManager.getBatchOperations({pageSize: 20});
  }, [dataManager]);

  // Subscribe to updates on batch operations
  const subscribeToOperations = useCallback(() => {
    subscribe(
      SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS,
      LOADING_STATE.LOADED,
      payload => {
        dispatch({type: ACTIONS.LOAD, payload});
      }
    );

    subscribe(
      SUBSCRIPTION_TOPIC.CREATE_BATCH_OPERATION,
      LOADING_STATE.LOADED,
      payload => {
        dispatch({type: ACTIONS.PREPEND, payload});
      }
    );

    return () => {
      unsubscribe();
      dataManager.poll.unregister(POLL_TOPICS.OPERATIONS);
    };
  }, [subscribe, unsubscribe, dataManager.poll]);

  useEffect(subscribeToOperations, []);

  // Register to polling, when there are running operations
  useEffect(() => {
    if (hasRunningBatchOperations(batchOperations)) {
      dataManager.poll.register(POLL_TOPICS.OPERATIONS, requestBatchOperations);
    } else {
      dataManager.poll.unregister(POLL_TOPICS.OPERATIONS);
    }
  }, [dataManager, batchOperations, requestBatchOperations]);

  return {
    batchOperations,
    requestBatchOperations
  };
}
