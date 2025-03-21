/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {extractFilePath} from './extractFilePath';

describe('extractFilePath', () => {
  it('should extract file paths from an object', () => {
    const mock = {
      a: 'files::123',
      b: {
        c: 'files::456',
        d: 'not a file',
      },
      e: [
        'files::789',
        {
          f: 'files::012',
        },
      ],
    };

    const result = extractFilePath(mock);

    expect(result.size).toBe(4);
    expect(result.get('files::123')).toBe('a');
    expect(result.get('files::456')).toBe('b.c');
    expect(result.get('files::789')).toBe('e[0]');
    expect(result.get('files::012')).toBe('e[1].f');
  });

  it('should handle objects with no file paths', () => {
    const mock = {
      a: 1,
      b: 'text',
      c: {
        d: true,
      },
    };

    const result = extractFilePath(mock);

    expect(result.size).toBe(0);
  });

  it('should handle empty objects', () => {
    expect(extractFilePath({}).size).toBe(0);
  });

  it('should handle null and undefined values in the object', () => {
    const mock = {
      a: null,
      b: undefined,
      c: 'files::789',
    };

    const result = extractFilePath(mock);

    expect(result.size).toBe(1);
    expect(result.get('files::789')).toBe('c');
  });

  it('should handle nested arrays and objects with file paths', () => {
    const mock = {
      deeply: {
        nested: {
          array: [
            {
              with: 'files::deep-file',
            },
          ],
        },
      },
    };

    const result = extractFilePath(mock);

    expect(result.size).toBe(1);
    expect(result.get('files::deep-file')).toBe('deeply.nested.array[0].with');
  });

  it('should handle primitive values as input', () => {
    expect(extractFilePath(null).size).toBe(0);
    expect(extractFilePath(undefined).size).toBe(0);
    expect(extractFilePath(123).size).toBe(0);
    expect(extractFilePath('string').size).toBe(0);
    expect(extractFilePath(true).size).toBe(0);
    expect(extractFilePath(Symbol('test')).size).toBe(0);
    expect(extractFilePath(BigInt(123)).size).toBe(0);
    expect(extractFilePath(() => {}).size).toBe(0);
    expect(extractFilePath(NaN).size).toBe(0);
    expect(extractFilePath(Infinity).size).toBe(0);
  });
});
