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
    expect(untruncateJson('"Hello"').completed).toBe('"Hello"');
  });

  it('returns unmodified valid string with bracket characters', () => {
    expect(untruncateJson('"}{]["').completed).toBe('"}{]["');
  });

  it('returns unmodified valid string with escaped quotes', () => {
    expect(untruncateJson('"\\"Dr.\\" Leo Spaceman"').completed).toBe(
      '"\\"Dr.\\" Leo Spaceman"',
    );
  });

  it('returns unmodified valid string with Unicode escapes', () => {
    expect(untruncateJson('ab\\u0065cd').completed).toBe('ab\\u0065cd');
  });

  it('returns unmodified valid number', () => {
    expect(untruncateJson('20').completed).toBe('20');
  });

  it('returns unmodified valid boolean', () => {
    expect(untruncateJson('true').completed).toBe('true');
    expect(untruncateJson('false').completed).toBe('false');
  });

  it('returns unmodified valid null', () => {
    expect(untruncateJson('null').completed).toBe('null');
  });

  it('returns unmodified valid array', () => {
    expect(untruncateJson('[]').completed).toBe('[]');
    expect(untruncateJson('["a", "b", "c"]').completed).toBe('["a", "b", "c"]');
    expect(untruncateJson('[ 1, 2, 3 ]').completed).toBe('[ 1, 2, 3 ]');
  });

  it('returns unmodified valid object', () => {
    expect(untruncateJson('{}').completed).toBe('{}');
    expect(untruncateJson('{"foo": "bar"}').completed).toBe('{"foo": "bar"}');
    expect(untruncateJson('{ "foo": 2 }').completed).toBe('{ "foo": 2 }');
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
    expect(untruncateJson(json).completed).toBe(json);
  });

  it('adds a missing close quote', () => {
    expect(untruncateJson('"Hello').completed).toBe('"Hello"');
  });

  it('cuts off trailing "\\" in a string', () => {
    expect(untruncateJson('"Hello\\').completed).toBe('"Hello"');
  });

  it('cuts off a partial Unicode escape in a string', () => {
    expect(untruncateJson('"ab\\u006').completed).toBe('"ab"');
  });

  it('adds "0" to a number cut off at a negative sign', () => {
    expect(untruncateJson('-').completed).toBe('-0');
  });

  it('adds "0" to a number cut off at a decimal point', () => {
    expect(untruncateJson('12.').completed).toBe('12.0');
  });

  it('adds "0" to a number cut off at an "e" or "E"', () => {
    expect(untruncateJson('12e').completed).toBe('12e0');
    expect(untruncateJson('12E').completed).toBe('12E0');
  });

  it('adds "0" to a number cut off after "e+" or "e-"', () => {
    expect(untruncateJson('12e+').completed).toBe('12e+0');
    expect(untruncateJson('12E-').completed).toBe('12E-0');
  });

  it('completes boolean and null literals', () => {
    expect(untruncateJson('tr').completed).toBe('true');
    expect(untruncateJson('fal').completed).toBe('false');
    expect(untruncateJson('nu').completed).toBe('null');
  });

  it('closes an empty list', () => {
    expect(untruncateJson('[').completed).toBe('[]');
  });

  it('closes a list with items', () => {
    expect(untruncateJson('["a", "b"').completed).toBe('["a", "b"]');
  });

  it('closes a list ending in a number', () => {
    expect(untruncateJson('[1, 2').completed).toBe('[1, 2]');
  });

  it('completes boolean and null literals at the end of a list', () => {
    expect(untruncateJson('[tr').completed).toBe('[true]');
    expect(untruncateJson('[true, fa').completed).toBe('[true, false]');
    expect(untruncateJson('[nul').completed).toBe('[null]');
  });

  it('removes a trailing comma to end a list', () => {
    expect(untruncateJson('[1, 2,').completed).toBe('[1, 2]');
  });

  it('closes an empty object', () => {
    expect(untruncateJson('{').completed).toBe('{}');
  });

  it('closes an object after key-value pairs', () => {
    expect(untruncateJson('{"a": "b"').completed).toBe('{"a": "b"}');
    expect(untruncateJson('{"a": 1').completed).toBe('{"a": 1}');
  });

  it('cuts off a partial key in an object', () => {
    expect(untruncateJson('{"hel').completed).toBe('{}');
    expect(untruncateJson('{"hello": 1, "wo').completed).toBe('{"hello": 1}');
  });

  it('cuts off a key missing a colon in an object', () => {
    expect(untruncateJson('{"hello"').completed).toBe('{}');
    expect(untruncateJson('{"hello": 1, "world"').completed).toBe(
      '{"hello": 1}',
    );
  });

  it('cuts off a key and colon without a value in an object', () => {
    expect(untruncateJson('{"hello":').completed).toBe('{}');
    expect(untruncateJson('{"hello": 1, "world": ').completed).toBe(
      '{"hello": 1}',
    );
  });

  it('untruncates a value in an object', () => {
    expect(untruncateJson('{"hello": "wo').completed).toBe('{"hello": "wo"}');
    expect(untruncateJson('{"hello": [1, 2').completed).toBe(
      '{"hello": [1, 2]}',
    );
  });

  it('handles a string in an array cut off at a "\\"', () => {
    expect(untruncateJson('["hello\\').completed).toBe('["hello"]');
    expect(untruncateJson('["hello", "world\\').completed).toBe(
      '["hello", "world"]',
    );
  });

  it('handles a cut off string in an array with an escaped character', () => {
    expect(
      untruncateJson('["hello", "\\"Dr.]\\" Leo Spaceman').completed,
    ).toBe('["hello", "\\"Dr.]\\" Leo Spaceman"]');
  });

  it('handles a string in an object key cut off at a "\\"', () => {
    expect(untruncateJson('{"hello\\').completed).toBe('{}');
    expect(untruncateJson('{"hello": 1, "world\\').completed).toBe(
      '{"hello": 1}',
    );
  });

  it('removes cut off object with key containing escaped characters', () => {
    expect(untruncateJson('{"hello\\nworld": ').completed).toBe('{}');
    expect(untruncateJson('{"hello": 1, "hello\\nworld').completed).toBe(
      '{"hello": 1}',
    );
  });

  it('returns collectionDepth 0 for non-truncated input', () => {
    expect(untruncateJson('{"a": 1}').collectionDepth).toBe(0);
    expect(untruncateJson('[1, 2, 3]').collectionDepth).toBe(0);
  });

  it('returns correct collectionDepth for truncated collections', () => {
    expect(untruncateJson('{').collectionDepth).toBe(1);
    expect(untruncateJson('[').collectionDepth).toBe(1);
    expect(untruncateJson('{"a": [').collectionDepth).toBe(2);
    expect(untruncateJson('[{"a":').collectionDepth).toBe(2);
  });
});
