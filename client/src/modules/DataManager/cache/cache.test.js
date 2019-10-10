/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import RequestCache from './cache';

import {
  requestDetails,
  newRequestDetails,
  requestName,
  requests,
  requestNames,
  requestDetailsByName
} from './cache.setup';

describe('Cache', () => {
  let cache;
  let cacheSetSpy;
  let cachegetEndpointsbyNamesSpy;
  beforeEach(() => {
    cache = new RequestCache();
    cacheSetSpy = jest.spyOn(cache, 'set');
    cachegetEndpointsbyNamesSpy = jest.spyOn(cache, 'getEndpointsbyNames');
  });

  it('should allow to cache request details by name', () => {
    cache.set(requestName, requestDetails);

    expect(cache.cache[requestName]).toMatchObject(requestDetails);
  });

  it('should overrwite request details when new data comes in', () => {
    // given
    cache.set(requestName, requestDetails);

    // when
    cache.set(requestName, newRequestDetails);

    //then
    expect(cache.cache[requestName]).toMatchObject(newRequestDetails);
  });

  it('should set params in cache and pass through', () => {
    const name = '';
    const params = {};
    const apiCall = '';
    const cachedParams = cache.update(name, apiCall, params);

    expect(cacheSetSpy.mock.calls[0][0]).toEqual(name, {params, apiCall});
    expect(cachedParams).toEqual(params);
  });

  it('should retrieve params from cache', () => {
    //given
    const name = 'NAME';
    const apiCall = 'apicallFunctionName';

    cache.update(name, apiCall);
    expect(cachegetEndpointsbyNamesSpy.mock.calls[0][0]).toEqual([name]);
  });

  it('should get cached details by key', () => {
    cache.set(requests[0].name, requests[0].details);
    cache.set(requests[1].name, requests[1].details);

    expect(cache.getEndpointsbyNames(requestNames)).toMatchObject(
      requestDetailsByName
    );
  });
});
