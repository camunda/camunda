/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {flowNodeTimeStampStore} from './flowNodeTimeStamp';

describe('stores/flowNodeTimeStamp', () => {
  afterEach(() => {
    flowNodeTimeStampStore.reset();
  });

  it('should toggle time stamp visibility', async () => {
    expect(flowNodeTimeStampStore.state.isTimeStampVisible).toBe(false);
    flowNodeTimeStampStore.toggleTimeStampVisibility();
    expect(flowNodeTimeStampStore.state.isTimeStampVisible).toBe(true);
  });

  it('should reset store', async () => {
    flowNodeTimeStampStore.toggleTimeStampVisibility();
    expect(flowNodeTimeStampStore.state.isTimeStampVisible).toBe(true);
    flowNodeTimeStampStore.reset();
    expect(flowNodeTimeStampStore.state.isTimeStampVisible).toBe(false);
  });
});
