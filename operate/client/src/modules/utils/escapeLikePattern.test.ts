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
  it('returns lowercase, UPPERCASE and Title Case variants for a lowercase input', () => {
    expect(escapeLikePatternsForCaseInsensitive('order').sort()).toEqual(
      ['*order*', '*ORDER*', '*Order*'].sort(),
    );
  });

  it('correctly title-cases multi-word input (each word capitalised)', () => {
    const patterns = escapeLikePatternsForCaseInsensitive('order task');
    expect(patterns).toContain('*Order Task*');
    expect(patterns).not.toContain('*Order task*');
  });

  it('emits the same set regardless of the casing of the input', () => {
    const fromLower = escapeLikePatternsForCaseInsensitive('order').sort();
    const fromUpper = escapeLikePatternsForCaseInsensitive('ORDER').sort();
    const fromTitle = escapeLikePatternsForCaseInsensitive('Order').sort();
    expect(fromLower).toEqual(fromUpper);
    expect(fromLower).toEqual(fromTitle);
  });

  it('deduplicates trivial inputs with no alpha characters', () => {
    expect(escapeLikePatternsForCaseInsensitive('1').sort()).toEqual(['*1*']);
  });

  it('escapes wildcards inside each variant', () => {
    const patterns = escapeLikePatternsForCaseInsensitive('a*b');
    // Every returned pattern must have the * escaped
    for (const p of patterns) {
      expect(p).toMatch(/\\\*/);
    }
  });
});
