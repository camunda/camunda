/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementTimeStampStore} from './elementTimeStamp';

describe('stores/elementTimeStamp', () => {
  afterEach(() => {
    elementTimeStampStore.reset();
  });

  it('should toggle time stamp visibility', async () => {
    expect(elementTimeStampStore.state.isTimeStampVisible).toBe(false);
    elementTimeStampStore.toggleTimeStampVisibility();
    expect(elementTimeStampStore.state.isTimeStampVisible).toBe(true);
  });

  it('should reset store', async () => {
    elementTimeStampStore.toggleTimeStampVisibility();
    expect(elementTimeStampStore.state.isTimeStampVisible).toBe(true);
    elementTimeStampStore.reset();
    expect(elementTimeStampStore.state.isTimeStampVisible).toBe(false);
  });
});
