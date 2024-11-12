/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
