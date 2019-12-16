/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {renderHook, act} from '@testing-library/react-hooks';

import {DataManagerProvider} from 'modules/DataManager';
import useDataManager from './';

import {DataManager} from 'modules/DataManager/core';
jest.mock('modules/DataManager/core');
jest.mock('modules/utils/bpmn');

const mockFunctions = {
  subscribe: jest.fn(),
  unsubscribe: jest.fn()
};

describe('useDataManager', () => {
  beforeEach(() => {
    DataManager.mockImplementation(() => mockFunctions);
  });

  it('should sanatize incoming statehooks', () => {
    const topic = 'SomeTopic';
    const singleStateHook = 'loaded';
    const callback = () => {
      //some logic
    };

    const {result} = renderHook(() => useDataManager(), {
      wrapper: DataManagerProvider
    });

    act(() => {
      result.current.subscribe(topic, singleStateHook, callback);
    });

    expect(mockFunctions.subscribe).toHaveBeenCalled();
  });

  it('should subscribe each single topic', () => {
    const topic = 'SomeTopic';
    const multipleStateHooks = ['loaded', 'loading'];
    const callback = () => {
      //some logic
    };

    const {result} = renderHook(() => useDataManager(), {
      wrapper: DataManagerProvider
    });

    act(() => {
      result.current.subscribe(topic, multipleStateHooks, callback);
    });

    expect(mockFunctions.subscribe).toHaveBeenCalled();
  });
});
