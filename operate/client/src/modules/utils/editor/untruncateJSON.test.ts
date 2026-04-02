/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {untruncateJson} from './untruncateJSON';

describe('untruncateJson', () => {
  it('returns unmodified valid string', () => {
    expect(untruncateJson('"Hello"')).toBe('"Hello"');
  });

  it('returns unmodified valid string with bracket characters', () => {
    expect(untruncateJson('"}{]["')).toBe('"}{]["');
  });

  it('returns unmodified valid string with escaped quotes', () => {
    expect(untruncateJson('"\\"Dr.\\" Leo Spaceman"')).toBe(
      '"\\"Dr.\\" Leo Spaceman"',
    );
  });

  it('returns unmodified valid string with Unicode escapes', () => {
    expect(untruncateJson('ab\\u0065cd')).toBe('ab\\u0065cd');
  });

  it('returns unmodified valid number', () => {
    expect(untruncateJson('20')).toBe('20');
  });

  it('returns unmodified valid boolean', () => {
    expect(untruncateJson('true')).toBe('true');
    expect(untruncateJson('false')).toBe('false');
  });

  it('returns unmodified valid null', () => {
    expect(untruncateJson('null')).toBe('null');
  });

  it('returns unmodified valid array', () => {
    expect(untruncateJson('[]')).toBe('[]');
    expect(untruncateJson('["a", "b", "c"]')).toBe('["a", "b", "c"]');
    expect(untruncateJson('[ 1, 2, 3 ]')).toBe('[ 1, 2, 3 ]');
  });

  it('returns unmodified valid object', () => {
    expect(untruncateJson('{}')).toBe('{}');
    expect(untruncateJson('{"foo": "bar"}')).toBe('{"foo": "bar"}');
    expect(untruncateJson('{ "foo": 2 }')).toBe('{ "foo": 2 }');
  });

  it('returns unmodified compound object', () => {
    const json = JSON.stringify({
      s: 'Hello',
      num: 10,
      b: true,
      nul: 'null',
      o: {s: 'Hello2', num: 11},
      a: ['Hello', 10, {s: 'Hello3'}],
    });
    expect(untruncateJson(json)).toBe(json);
  });

  it('adds a missing close quote', () => {
    expect(untruncateJson('"Hello')).toBe('"Hello"');
  });

  it('cuts off trailing "\\" in a string', () => {
    expect(untruncateJson('"Hello\\')).toBe('"Hello"');
  });

  it('cuts off a partial Unicode escape in a string', () => {
    expect(untruncateJson('"ab\\u006')).toBe('"ab"');
  });

  it('adds "0" to a number cut off at a negative sign', () => {
    expect(untruncateJson('-')).toBe('-0');
  });

  it('adds "0" to a number cut off at a decimal point', () => {
    expect(untruncateJson('12.')).toBe('12.0');
  });

  it('adds "0" to a number cut off at an "e" or "E"', () => {
    expect(untruncateJson('12e')).toBe('12e0');
    expect(untruncateJson('12E')).toBe('12E0');
  });

  it('adds "0" to a number cut off after "e+" or "e-"', () => {
    expect(untruncateJson('12e+')).toBe('12e+0');
    expect(untruncateJson('12E-')).toBe('12E-0');
  });

  it('completes boolean and null literals', () => {
    expect(untruncateJson('tr')).toBe('true');
    expect(untruncateJson('fal')).toBe('false');
    expect(untruncateJson('nu')).toBe('null');
  });

  it('closes an empty list', () => {
    expect(untruncateJson('[')).toBe('[]');
  });

  it('closes a list with items', () => {
    expect(untruncateJson('["a", "b"')).toBe('["a", "b"]');
  });

  it('closes a list ending in a number', () => {
    expect(untruncateJson('[1, 2')).toBe('[1, 2]');
  });

  it('completes boolean and null literals at the end of a list', () => {
    expect(untruncateJson('[tr')).toBe('[true]');
    expect(untruncateJson('[true, fa')).toBe('[true, false]');
    expect(untruncateJson('[nul')).toBe('[null]');
  });

  it('removes a trailing comma to end a list', () => {
    expect(untruncateJson('[1, 2,')).toBe('[1, 2]');
  });

  it('closes an empty object', () => {
    expect(untruncateJson('{')).toBe('{}');
  });

  it('closes an object after key-value pairs', () => {
    expect(untruncateJson('{"a": "b"')).toBe('{"a": "b"}');
    expect(untruncateJson('{"a": 1')).toBe('{"a": 1}');
  });

  it('cuts off a partial key in an object', () => {
    expect(untruncateJson('{"hel')).toBe('{}');
    expect(untruncateJson('{"hello": 1, "wo')).toBe('{"hello": 1}');
  });

  it('cuts off a key missing a colon in an object', () => {
    expect(untruncateJson('{"hello"')).toBe('{}');
    expect(untruncateJson('{"hello": 1, "world"')).toBe('{"hello": 1}');
  });

  it('cuts off a key and colon without a value in an object', () => {
    expect(untruncateJson('{"hello":')).toBe('{}');
    expect(untruncateJson('{"hello": 1, "world": ')).toBe('{"hello": 1}');
  });

  it('untruncates a value in an object', () => {
    expect(untruncateJson('{"hello": "wo')).toBe('{"hello": "wo"}');
    expect(untruncateJson('{"hello": [1, 2')).toBe('{"hello": [1, 2]}');
  });

  it('handles a string in an array cut off at a "\\"', () => {
    expect(untruncateJson('["hello\\')).toBe('["hello"]');
    expect(untruncateJson('["hello", "world\\')).toBe('["hello", "world"]');
  });

  it('handles a cut off string in an array with an escaped character', () => {
    expect(untruncateJson('["hello", "\\"Dr.]\\" Leo Spaceman')).toBe(
      '["hello", "\\"Dr.]\\" Leo Spaceman"]',
    );
  });

  it('handles a string in an object key cut off at a "\\"', () => {
    expect(untruncateJson('{"hello\\')).toBe('{}');
    expect(untruncateJson('{"hello": 1, "world\\')).toBe('{"hello": 1}');
  });

  it('removes cut off object with key containing escaped characters', () => {
    expect(untruncateJson('{"hello\\nworld": ')).toBe('{}');
    expect(untruncateJson('{"hello": 1, "hello\\nworld')).toBe('{"hello": 1}');
  });
});
