/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  escapeLikePattern,
  escapeLikePatternsForCaseInsensitive,
} from './escapeLikePattern';

describe('escapeLikePattern', () => {
  it('wraps plain ASCII input in substring wildcards', () => {
    expect(escapeLikePattern('foo')).toBe('*foo*');
  });

  it('escapes the literal $like wildcard `*`', () => {
    expect(escapeLikePattern('foo*bar')).toBe('*foo\\*bar*');
  });

  it('escapes the literal single-character wildcard `?`', () => {
    expect(escapeLikePattern('foo?bar')).toBe('*foo\\?bar*');
  });

  it('escapes the literal escape character `\\`', () => {
    expect(escapeLikePattern('foo\\bar')).toBe('*foo\\\\bar*');
  });

  it('passes Unicode and surrogate-pair characters through unchanged', () => {
    expect(escapeLikePattern('pizza🍕')).toBe('*pizza🍕*');
  });

  it('passes RTL characters through unchanged', () => {
    expect(escapeLikePattern('‮order')).toBe('*‮order*');
  });

  it('handles an empty string', () => {
    expect(escapeLikePattern('')).toBe('**');
  });
});

describe('escapeLikePatternsForCaseInsensitive', () => {
  it('emits as-typed, lowercase, UPPERCASE, and Title case variants for an all-lowercase input', () => {
    expect(escapeLikePatternsForCaseInsensitive('order').sort()).toEqual(
      ['*order*', '*Order*', '*ORDER*'].sort(),
    );
  });

  it('emits the same set regardless of input casing', () => {
    const fromLower = escapeLikePatternsForCaseInsensitive('order').sort();
    const fromUpper = escapeLikePatternsForCaseInsensitive('ORDER').sort();
    const fromTitle = escapeLikePatternsForCaseInsensitive('Order').sort();
    expect(fromLower).toEqual(fromUpper);
    expect(fromLower).toEqual(fromTitle);
  });

  it('deduplicates trivial inputs', () => {
    // A single non-letter character has no case variant
    expect(escapeLikePatternsForCaseInsensitive('1').sort()).toEqual(['*1*']);
  });

  it('escapes wildcards inside each variant', () => {
    // as-typed "a*b", lower "a*b", upper "A*B", title-case "A*b" -> 3 unique
    expect(escapeLikePatternsForCaseInsensitive('a*b').sort()).toEqual(
      ['*a\\*b*', '*A\\*B*', '*A\\*b*'].sort(),
    );
  });
});
