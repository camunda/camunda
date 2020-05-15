/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {storeStateLocally, getStateLocally, clearStateLocally} from '.';

describe('localStorage', () => {
  it('should store state in localstorage', () => {
    const data = {a: 1, b: 2};

    storeStateLocally(data);

    expect(getStateLocally()).toEqual(data);
  });

  it('should retrieve localstorage state', () => {
    storeStateLocally({a: 1, b: 2});
    expect(getStateLocally()).toEqual({a: 1, b: 2});
  });

  it('should clear localstorage state', () => {
    localStorage.removeItem.mockClear();

    clearStateLocally();

    expect(localStorage.removeItem).toHaveBeenCalledWith('sharedState');
  });
});
