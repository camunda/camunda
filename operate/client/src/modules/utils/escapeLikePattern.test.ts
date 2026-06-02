/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {escapeLikePattern} from './escapeLikePattern';

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
