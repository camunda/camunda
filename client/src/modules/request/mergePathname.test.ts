/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mergePathname} from './mergePathname';

describe('mergePathname', () => {
  it('should merge a prefix to a pathname', () => {
    expect(mergePathname('foo/', '/bar')).toBe('foo/bar');
    expect(mergePathname('foo', '/bar')).toBe('foo/bar');
    expect(mergePathname('foo/', 'bar')).toBe('foo/bar');
    expect(mergePathname('foo', 'bar')).toBe('foo/bar');
    expect(mergePathname('/', '')).toBe('/');
    expect(mergePathname('', '/')).toBe('/');
    expect(mergePathname('/foo', '')).toBe('/foo/');
    expect(mergePathname('', '/bar')).toBe('/bar');
    expect(mergePathname('foo/', '')).toBe('foo/');
    expect(mergePathname('', 'bar/')).toBe('/bar/');
    expect(mergePathname('', '')).toBe('/');
  });
});
