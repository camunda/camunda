/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {deleteSearchParams, parseFilterTime} from '../index';

describe('utils/filter', () => {
  it('should delete search params', () => {
    const locationMock = {
      hash: '',
      pathname: '/processes',
      search: 'test=1&test2=2&test3=3',
      state: null,
      key: '',
    } as const;

    expect(deleteSearchParams(locationMock, ['test2', 'test3']).search).toBe(
      'test=1',
    );
    expect(deleteSearchParams(locationMock, ['test2']).search).toBe(
      'test=1&test3=3',
    );
    expect(deleteSearchParams(locationMock, ['test4']).search).toBe(
      'test=1&test2=2&test3=3',
    );
  });

  it('should validate time', () => {
    expect(parseFilterTime('23:59:59')).not.toBeUndefined();
    expect(parseFilterTime('00:00:00')).not.toBeUndefined();
    expect(parseFilterTime('12:34:56')).not.toBeUndefined();
    expect(parseFilterTime('00:00')).not.toBeUndefined();
    expect(parseFilterTime('23:59')).not.toBeUndefined();

    expect(parseFilterTime('aa')).toBeUndefined();
    expect(parseFilterTime('123456')).toBeUndefined();
    expect(parseFilterTime('0')).toBeUndefined();
    expect(parseFilterTime('99:99:99')).toBeUndefined();
    expect(parseFilterTime('66:66')).toBeUndefined();
    expect(parseFilterTime('')).toBeUndefined();
    expect(parseFilterTime('   ')).toBeUndefined();
    expect(parseFilterTime(':::')).toBeUndefined();
    expect(parseFilterTime('-')).toBeUndefined();
  });
});
