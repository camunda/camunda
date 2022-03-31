/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {storeStateLocally, getStateLocally, clearStateLocally} from '.';

const KEY_NAME = 'someKey';

describe('localStorage', () => {
  afterAll(() => {
    clearStateLocally(KEY_NAME);
  });

  it('should manage local storage', () => {
    const data = {a: 1, b: 2};

    expect(getStateLocally(KEY_NAME)).toEqual(null);

    storeStateLocally(KEY_NAME, data);
    expect(getStateLocally(KEY_NAME)).toEqual(data);

    clearStateLocally(KEY_NAME);
    expect(getStateLocally(KEY_NAME)).toEqual(null);
  });
});
