/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {renderHook, act} from '@testing-library/react-hooks';
import useBatchOperations from './useBatchOperations';

import useSubscription from 'modules/hooks/useSubscription';
import useDataManager from 'modules/hooks/useDataManager';

import {SUBSCRIPTION_TOPIC, LOADING_STATE} from 'modules/constants';

import {
  mockOperationFinished,
  mockOperationRunning
} from './OperationsPanel.setup';

jest.mock('modules/hooks/useSubscription');
jest.mock('modules/hooks/useDataManager');

describe('useBatchOperations', () => {
  beforeEach(() => {
    useSubscription.mockReturnValue({
      subscribe: jest.fn(),
      unsubscribe: jest.fn()
    });

    useDataManager.mockReturnValue({
      poll: {
        register: jest.fn(),
        unregister: jest.fn()
      },
      getBatchOperations: jest.fn()
    });
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  it('should subscribe to data manager updates on first render', () => {
    // when
    const {result} = renderHook(() => useBatchOperations(), {});

    // then
    expect(result.current.batchOperations).toEqual([]);
    expect(useSubscription().subscribe).toHaveBeenCalledTimes(2);
    expect(useSubscription().subscribe).toHaveBeenNthCalledWith(
      1,
      SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS,
      LOADING_STATE.LOADED,
      expect.any(Function)
    );

    expect(useSubscription().subscribe).toHaveBeenNthCalledWith(
      2,
      SUBSCRIPTION_TOPIC.CREATE_BATCH_OPERATION,
      LOADING_STATE.LOADED,
      expect.any(Function)
    );
  });

  it('should register for polling when there running operations', () => {
    // given
    // simulate a publish after subscribing
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        callback([mockOperationRunning]);
      }
    );

    // when
    const {result} = renderHook(() => useBatchOperations(), {});

    // then
    expect(result.current.batchOperations).toEqual([mockOperationRunning]);
    expect(useDataManager().poll.register).toHaveBeenCalledTimes(1);
  });

  it('should not register for polling when there no running operations', () => {
    // given
    // simulate a publish after subscribing
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        callback([mockOperationFinished]);
      }
    );

    // when
    const {result} = renderHook(() => useBatchOperations(), {});

    // then
    expect(result.current.batchOperations).toEqual([mockOperationFinished]);
    expect(useDataManager().poll.register).not.toHaveBeenCalled();
  });

  it('should register for polling when operations change from finished to running', () => {
    // given
    let publish;
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS) {
          publish = callback;
        }
      }
    );
    const {result} = renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish([mockOperationFinished]);
    });

    act(() => {
      publish([mockOperationRunning]);
    });

    // then
    expect(result.current.batchOperations).toEqual([mockOperationRunning]);
    expect(useDataManager().poll.register).toHaveBeenCalledTimes(1);
  });

  it('should unregister from polling when operations change from running to finished', () => {
    // given
    let publish;
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS) {
          publish = callback;
        }
      }
    );
    const {result} = renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish([mockOperationRunning]);
    });

    act(() => {
      publish([mockOperationFinished]);
    });

    // then
    expect(result.current.batchOperations).toEqual([mockOperationFinished]);
    expect(useDataManager().poll.register).toHaveBeenCalledTimes(1);
    expect(useDataManager().poll.unregister).toHaveBeenCalledTimes(2);
  });

  it('should unregister from polling on unmount', () => {
    // given
    const {unmount} = renderHook(() => useBatchOperations(), {});

    // when
    unmount();

    // then
    expect(useDataManager().poll.unregister).toHaveBeenCalledTimes(2);
  });

  it('should get batch operations on CREATE_BATCH_OPERATION publish', () => {
    // given
    let publish;

    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.CREATE_BATCH_OPERATION) {
          publish = callback;
        }
      }
    );

    renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish();
    });

    // then
    expect(useDataManager().getBatchOperations).toHaveBeenCalledTimes(1);
  });
});
