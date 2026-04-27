/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {storeStateLocally, getStateLocally, clearStateLocally} from '.';

const KEY_NAME = 'theme';

describe('localStorage', () => {
  afterAll(() => {
    clearStateLocally(KEY_NAME);
  });

  it('should manage local storage', () => {
    const data = 'dark';

    expect(getStateLocally(KEY_NAME)).toEqual(null);

    storeStateLocally(KEY_NAME, data);
    expect(getStateLocally(KEY_NAME)).toEqual(data);

    clearStateLocally(KEY_NAME);
    expect(getStateLocally(KEY_NAME)).toEqual(null);
  });
});
