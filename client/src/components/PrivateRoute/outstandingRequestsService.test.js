/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {request} from 'request';

import {
  createOutstandingRequestPromise,
  redoOutstandingRequests,
} from './outstandingRequestsService';

jest.mock('request', () => ({request: jest.fn()}));

beforeEach(() => {
  jest.clearAllMocks();
});

it('should resolve all the created outstanding requests promises', () => {
  createOutstandingRequestPromise({url: 'api/request1'});
  createOutstandingRequestPromise({url: 'api/request2'});

  redoOutstandingRequests();

  expect(request).toHaveBeenCalledWith({url: 'api/request1'});
  expect(request).toHaveBeenCalledWith({url: 'api/request2'});

  request.mockClear();
  redoOutstandingRequests();
  expect(request).not.toHaveBeenCalled();
});
