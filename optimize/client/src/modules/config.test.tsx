/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as config from './config';

jest.mock('request', () => ({
  get: jest.fn().mockReturnValue({
    json: () => ({
      optimizeVersion: '2.7.0',
    }),
  }),
}));

it('should make the configuration available', () => {
  expect(config.getOptimizeVersion()).resolves.toEqual('2.7.0');
});
