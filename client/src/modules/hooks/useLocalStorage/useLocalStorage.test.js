/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {renderHook, act} from '@testing-library/react-hooks';
import useLocalStorage from './';

describe('useLocalStorage', () => {
  const mockStorageKey = 'someStorageKey';
  const dataToStore = {a: 1, b: 2};

  it('should set stored Value as default value', () => {
    const {result} = renderHook(() => useLocalStorage(mockStorageKey));

    expect(result.current.storedValue).toEqual({});
  });

  it('should store state in localstorage', () => {
    const {result} = renderHook(() => useLocalStorage());

    act(() => {
      result.current.setLocalStorage(dataToStore, mockStorageKey);
    });

    expect(result.current.storedValue).toEqual(dataToStore);
  });

  it('should clear all of local straoge values', () => {
    const {result} = renderHook(() => useLocalStorage(mockStorageKey));

    act(() => {
      result.current.setLocalStorage(dataToStore, mockStorageKey);
    });

    expect(result.current.storedValue).toEqual(dataToStore);

    act(() => {
      result.current.clearValue();
    });

    expect(result.current.storedValue).toEqual({});
  });
});
