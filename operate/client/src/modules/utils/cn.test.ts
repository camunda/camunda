/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cn} from './cn';

describe('cn', () => {
  it('should join multiple string arguments', () => {
    expect(cn('a', 'b', 'c')).toBe('a b c');
  });

  it('should filter out falsy values', () => {
    expect(cn('a', undefined, null, false, 'b')).toBe('a b');
  });

  it('should include object keys with truthy values', () => {
    expect(cn({foo: true, bar: false, baz: true})).toBe('foo baz');
  });

  it('should handle mixed string and object arguments', () => {
    expect(
      cn('cds--tabs__nav-item', 'cds--tabs__nav-link', {
        hidden: false,
        'cds--tabs__nav-item--selected': true,
      }),
    ).toBe(
      'cds--tabs__nav-item cds--tabs__nav-link cds--tabs__nav-item--selected',
    );
  });

  it('should return an empty string when called with no arguments', () => {
    expect(cn()).toBe('');
  });

  it('should return an empty string when all arguments are falsy', () => {
    expect(cn(undefined, null, false)).toBe('');
  });
});
