/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';
import * as config from './config';

jest.mock('request', () => ({
  get: jest.fn().mockReturnValue({
    json: () => ({
      optimizeVersion: '2.7.0',
    }),
  }),
}));

it('should load the configuration from the server', () => {
  expect(get).toHaveBeenCalled();
});

it('should make the configuration available', () => {
  expect(config.getOptimizeVersion()).resolves.toEqual('2.7.0');
});
