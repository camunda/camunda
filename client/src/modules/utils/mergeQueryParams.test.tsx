/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mergeQueryParams} from './mergeQueryParams';

describe('mergeQueryParams', () => {
  it('should merge query params', () => {
    expect(
      mergeQueryParams({
        newParams: '?test=123&test2=456',
        prevParams: '?gseUrl=https://www.testUrl.com',
      })
    ).toBe('test=123&test2=456&gseUrl=https%3A%2F%2Fwww.testUrl.com');

    expect(
      mergeQueryParams({
        newParams: '?test=123&test2=456',
        prevParams: '?gseUrl=https://www.testUrl.com&someOther=1',
      })
    ).toBe(
      'test=123&test2=456&gseUrl=https%3A%2F%2Fwww.testUrl.com&someOther=1'
    );

    expect(
      mergeQueryParams({
        newParams: '',
        prevParams: '?gseUrl=https://www.testUrl.com&someOther=1',
      })
    ).toBe('gseUrl=https%3A%2F%2Fwww.testUrl.com&someOther=1');

    expect(
      mergeQueryParams({
        newParams:
          '?filter={"errorMessage":"No more retries left.","incidents":true}',
        prevParams: '?gseUrl=https://www.testUrl.com',
      })
    ).toBe(
      'filter=%7B%22errorMessage%22%3A%22No+more+retries+left.%22%2C%22incidents%22%3Atrue%7D&gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });
});
