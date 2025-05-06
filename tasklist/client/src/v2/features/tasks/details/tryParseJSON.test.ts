/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {tryParseJSON} from './tryParseJSON';

describe('tryParseJSON', () => {
  it('should parse valid JSON objects', () => {
    const jsonObject = '{"name": "John", "age": 30}';
    expect(tryParseJSON(jsonObject)).toEqual({name: 'John', age: 30});
  });

  it('should parse valid JSON arrays', () => {
    const jsonArray = '[1, 2, 3, 4]';
    expect(tryParseJSON(jsonArray)).toEqual([1, 2, 3, 4]);
  });

  it('should parse valid JSON primitives', () => {
    expect(tryParseJSON('123')).toBe(123);
    expect(tryParseJSON('0')).toBe(0);
    expect(tryParseJSON('-456')).toBe(-456);
    expect(tryParseJSON('3.14')).toBe(3.14);
    expect(tryParseJSON('-2.718')).toBe(-2.718);
    expect(tryParseJSON('0.5')).toBe(0.5);
    expect(tryParseJSON('true')).toBe(true);
    expect(tryParseJSON('null')).toBe(null);
    expect(tryParseJSON('"hello"')).toBe('hello');
    expect(tryParseJSON('"123"')).toBe('123');
  });

  it('should return the original string if parsing fails', () => {
    const invalidJson = '{name: John}';
    expect(tryParseJSON(invalidJson)).toBe(invalidJson);
  });

  it('should return the original string for non-JSON content', () => {
    const nonJson = 'Hello, world!';
    expect(tryParseJSON(nonJson)).toBe(nonJson);
  });
});
