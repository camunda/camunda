/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {fromCache, toCache} from './backendCache';

it('should return cached responses', () => {
  const response = {clone: () => response};
  toCache({url: 'api/meta/version'}, response);

  const cachedResponse = fromCache({url: 'api/meta/version'});

  expect(cachedResponse).toBe(response);
});

it('should not cache responses that are not on the whitelist', () => {
  const response = {clone: () => response};
  toCache({url: 'api/dashboard'}, response);

  const cachedResponse = fromCache({url: 'api/dashboard'});

  expect(cachedResponse).toBe(undefined);
});

it('should respect query parameters', () => {
  const response1 = {clone: () => response1};
  const response2 = {clone: () => response2};

  toCache({url: 'api/decision-definition/xml', query: {key: 'a', version: 1}}, response1);
  toCache({url: 'api/decision-definition/xml', query: {key: 'a', version: 2}}, response2);

  expect(fromCache({url: 'api/decision-definition/xml', query: {key: 'b', version: 1}})).toBe(
    undefined
  );
  expect(fromCache({url: 'api/decision-definition/xml', query: {key: 'a', version: 1}})).toBe(
    response1
  );
  expect(fromCache({url: 'api/decision-definition/xml', query: {key: 'a', version: 2}})).toBe(
    response2
  );
});
