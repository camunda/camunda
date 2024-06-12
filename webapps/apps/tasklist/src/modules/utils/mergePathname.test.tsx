/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
