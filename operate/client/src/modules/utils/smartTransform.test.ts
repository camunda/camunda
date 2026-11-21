/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {smartTransformValue, toStringFilterProperty} from './smartTransform';

describe('smartTransformValue', () => {
  it('should auto-quote a bare string', () => {
    expect(smartTransformValue('hello')).toBe('hello');
  });

  it('should parse a numeric literal as a number', () => {
    expect(smartTransformValue('42')).toBe(42);
  });

  it('should parse boolean literals as booleans', () => {
    expect(smartTransformValue('true')).toBe(true);
    expect(smartTransformValue('false')).toBe(false);
  });

  it('should parse null literal as null', () => {
    expect(smartTransformValue('null')).toBeNull();
  });

  it('should keep an explicitly-quoted string as a string', () => {
    expect(smartTransformValue('"hello"')).toBe('hello');
  });

  it('should split a bare comma list into an array', () => {
    expect(smartTransformValue('val1, val2')).toEqual(['val1', 'val2']);
  });

  it('should parse each element of a comma list independently', () => {
    expect(smartTransformValue('1, two, true')).toEqual([1, 'two', true]);
  });

  it('should strip leading and trailing commas before parsing', () => {
    expect(smartTransformValue(',hello,')).toBe('hello');
    expect(smartTransformValue(',a, b,')).toEqual(['a', 'b']);
  });

  it('should parse valid JSON objects and arrays', () => {
    expect(smartTransformValue('{"x":1}')).toEqual({x: 1});
    expect(smartTransformValue('[1,2,3]')).toEqual([1, 2, 3]);
  });

  it('should return empty string for empty or whitespace-only input', () => {
    expect(smartTransformValue('')).toBe('');
    expect(smartTransformValue('   ')).toBe('');
  });

  it('should throw on structurally-JSON-looking but invalid input', () => {
    expect(() => smartTransformValue('{not json')).toThrow();
  });

  it('should keep leading-zero strings as strings (documented hazard)', () => {
    expect(smartTransformValue('01234')).toBe('01234');
    expect(smartTransformValue('1234')).toBe(1234);
  });

  it('should treat a quoted comma-string as a single string', () => {
    expect(smartTransformValue('"a, b"')).toBe('a, b');
  });

  it('should keep integers up to the safe limit as numbers', () => {
    expect(smartTransformValue('9007199254740991')).toBe(
      Number.MAX_SAFE_INTEGER,
    );
    expect(smartTransformValue('-9007199254740991')).toBe(
      Number.MIN_SAFE_INTEGER,
    );
  });

  it('should keep the exact value of integers beyond the safe limit', () => {
    expect(smartTransformValue('9007199254740993')).toBe(9007199254740993n);
    expect(smartTransformValue('-9223372036854775808')).toBe(
      -9223372036854775808n,
    );
    expect(smartTransformValue('1234567890123456789, two')).toEqual([
      1234567890123456789n,
      'two',
    ]);
    expect(smartTransformValue('[1234567890123456789, 2]')).toEqual([
      1234567890123456789n,
      2,
    ]);
    expect(smartTransformValue('{"a":{"b":[1234567890123456789]}}')).toEqual({
      a: {b: [1234567890123456789n]},
    });
  });

  it('should leave digits inside of strings untouched', () => {
    expect(smartTransformValue('"1234567890123456789"')).toBe(
      '1234567890123456789',
    );
    expect(
      smartTransformValue(
        '{"1234567890123456789":"a \\"1234567890123456789\\" b"}',
      ),
    ).toEqual({'1234567890123456789': 'a "1234567890123456789" b'});
  });

  it('should keep inputs that only contain large digit runs as strings', () => {
    expect(smartTransformValue('order-1234567890123456789')).toBe(
      'order-1234567890123456789',
    );
    expect(smartTransformValue('01234567890123456789')).toBe(
      '01234567890123456789',
    );
  });

  it('should throw on invalid JSON containing a large integer', () => {
    expect(() => smartTransformValue('{"a":1234567890123456789')).toThrow();
  });
});

describe('toStringFilterProperty', () => {
  it('should map equals to $eq with JSON-encoded value', () => {
    expect(toStringFilterProperty('equals', 'active')).toEqual({
      $eq: '"active"',
    });
    expect(toStringFilterProperty('equals', 42)).toEqual({$eq: '42'});
    expect(toStringFilterProperty('equals', true)).toEqual({$eq: 'true'});
    expect(toStringFilterProperty('equals', null)).toEqual({$eq: 'null'});
    expect(toStringFilterProperty('equals', {x: 1})).toEqual({
      $eq: '{"x":1}',
    });
  });

  it('should map notEqual to $neq with JSON-encoded value', () => {
    expect(toStringFilterProperty('notEqual', 0)).toEqual({$neq: '0'});
    expect(toStringFilterProperty('notEqual', 'foo')).toEqual({
      $neq: '"foo"',
    });
  });

  it('should map contains to $like with wildcards wrapped around the raw value', () => {
    expect(toStringFilterProperty('contains', 'abc')).toEqual({
      $like: '*abc*',
    });
  });

  it('should pass user-typed wildcards through $like unescaped', () => {
    expect(toStringFilterProperty('contains', 'order-*')).toEqual({
      $like: '*order-**',
    });
    expect(toStringFilterProperty('contains', 'C?T')).toEqual({
      $like: '*C?T*',
    });
  });

  it('should map oneOf to $in with each element JSON-encoded', () => {
    expect(toStringFilterProperty('oneOf', ['gold', 'silver'])).toEqual({
      $in: ['"gold"', '"silver"'],
    });
    expect(toStringFilterProperty('oneOf', [1, 2, 3])).toEqual({
      $in: ['1', '2', '3'],
    });
  });

  it('should wrap a non-array value in a single-element array for oneOf', () => {
    expect(toStringFilterProperty('oneOf', 'only')).toEqual({
      $in: ['"only"'],
    });
  });

  it.each([
    ['a bare integer', '1234567890123456789'],
    ['a negative integer', '-9223372036854775808'],
    ['a just-unsafe integer', '9007199254740993'],
    ['a nested integer', '{"a":{"b":[1234567890123456789]},"c":"d"}'],
  ])('should not round %s in $eq and $neq', (_, input) => {
    const value = smartTransformValue(input);

    expect(toStringFilterProperty('equals', value)).toEqual({$eq: input});
    expect(toStringFilterProperty('notEqual', value)).toEqual({$neq: input});
  });

  it.each([
    ['a comma list', '1234567890123456789, two'],
    ['a JSON array', '[1234567890123456789, "two"]'],
  ])('should not round elements of %s in $in', (_, input) => {
    expect(toStringFilterProperty('oneOf', smartTransformValue(input))).toEqual(
      {$in: ['1234567890123456789', '"two"']},
    );
  });

  it('should normalize whitespace of JSON values like JSON.stringify', () => {
    expect(
      toStringFilterProperty(
        'equals',
        smartTransformValue('{ "x" : [1, null] }'),
      ),
    ).toEqual({$eq: '{"x":[1,null]}'});
  });

  it('should map exists to $exists true (ignoring value)', () => {
    expect(toStringFilterProperty('exists', undefined)).toEqual({
      $exists: true,
    });
  });

  it('should map doesNotExist to $exists false (ignoring value)', () => {
    expect(toStringFilterProperty('doesNotExist', undefined)).toEqual({
      $exists: false,
    });
  });
});
