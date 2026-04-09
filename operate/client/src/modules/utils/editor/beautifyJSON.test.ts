/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {beautifyJSON, beautifyTruncatedJSON} from './beautifyJSON';

describe('beautifyJSON', () => {
  it('pretty-prints a compact JSON object', () => {
    expect(beautifyJSON('{"a":1,"b":2}')).toBe('{\n\t"a": 1,\n\t"b": 2\n}');
  });

  it('returns the original value when JSON is invalid', () => {
    const invalid = 'not-json';

    expect(beautifyJSON(invalid)).toBe(invalid);
  });
});

describe('beautifyTruncatedJSON', () => {
  it('returns fully pretty-printed output for complete JSON', () => {
    expect(beautifyTruncatedJSON('{"a":1}')).toBe('{\n\t"a": 1\n}');
    expect(beautifyTruncatedJSON('[1,2,3]')).toBe('[\n\t1,\n\t2,\n\t3\n]');
  });

  it('strips the synthesized closing brace for a truncated object', () => {
    const result = beautifyTruncatedJSON('{"a": 1');

    expect(result).toBe('{\n\t"a": 1\n');
  });

  it('strips the synthesized closing bracket for a truncated array', () => {
    const result = beautifyTruncatedJSON('[1, 2');

    expect(result).toBe('[\n\t1,\n\t2\n');
  });

  it('strips multiple synthesized closing tokens for nested truncated collections', () => {
    const result = beautifyTruncatedJSON('{"a": [1, 2');

    expect(result).toBe('{\n\t"a": [\n\t\t1,\n\t\t2\n\t');
  });

  it('preserves inline closers that are real content when only one depth is synthesized', () => {
    const result = beautifyTruncatedJSON('{"a": []');

    expect(result).toContain('"a": []');
    expect(result).not.toMatch(/\}$/);
  });

  it('returns the original value when JSON cannot be parsed', () => {
    const garbage = '{{not json at all';

    expect(beautifyTruncatedJSON(garbage)).toBe(garbage);
  });
});
