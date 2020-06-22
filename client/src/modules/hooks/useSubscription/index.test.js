/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {renderHook, act} from '@testing-library/react-hooks';

import useSubscription from './index';

import useDataManager from 'modules/hooks/useDataManager';
jest.mock('modules/hooks/useDataManager');

const mockFunctions = {
  subscribe: jest.fn(),
  unsubscribe: jest.fn(),
};

describe('useSubscription', () => {
  beforeEach(() => {
    jest.resetAllMocks();
    useDataManager.mockImplementation(() => mockFunctions);
  });

  it('should sanatize incoming statehooks', () => {
    const topic = 'SomeTopic';
    const singleStateHook = 'loaded';
    const callback = () => {};

    const {result} = renderHook(() => useSubscription(), {});

    act(() => {
      result.current.subscribe(topic, singleStateHook, callback);
    });

    expect(mockFunctions.subscribe).toHaveBeenCalledTimes(1);
    expect(mockFunctions.subscribe).toHaveBeenCalledWith({
      SomeTopic: expect.any(Function),
    });
  });

  it('should subscribe each single topic', () => {
    const topic = 'SomeTopic';
    const multipleStateHooks = ['loaded', 'loading'];
    const callback = () => {};

    const {result} = renderHook(() => useSubscription(), {});

    act(() => {
      result.current.subscribe(topic, multipleStateHooks, callback);
    });

    expect(mockFunctions.subscribe).toHaveBeenCalledTimes(1);
    expect(mockFunctions.subscribe).toHaveBeenCalledWith({
      SomeTopic: expect.any(Function),
    });
  });

  it('should return an unsubscribe function which calls dataManager.unsubscribe', () => {
    const topic = 'SomeTopic';
    const stateHook = 'loaded';
    const callback = () => {};

    const {result} = renderHook(() => useSubscription(), {});

    let unsubscribe;

    act(() => {
      unsubscribe = result.current.subscribe(topic, stateHook, callback);
    });

    act(() => {
      unsubscribe();
    });

    expect(mockFunctions.subscribe).toHaveBeenCalledTimes(1);
    expect(mockFunctions.subscribe).toHaveBeenCalledWith({
      SomeTopic: expect.any(Function),
    });
    expect(mockFunctions.unsubscribe).toHaveBeenCalledTimes(1);
  });
});
