/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mergeVariables} from './mergeVariables';

describe('mergeVariables', () => {
  it('should merge variables', () => {
    const initialVariables = {
      a: 'a',
      b: 'b',
      c: 'c',
      d: 'd',
    };
    const newVariables = {
      a: 'a1',
      b: 'b1',
      c: 'c1',
    };

    expect(mergeVariables(initialVariables, newVariables)).toEqual({
      a: 'a1',
      b: 'b1',
      c: 'c1',
    });
  });

  it('should replace arrays', () => {
    const initialVariables = {
      a: ['a', 'b', 'c'],
      b: 'b',
      c: 'c',
    };
    const newVariables = {
      a: ['a1', 'b1', 'c1'],
      b: 'b1',
    };

    expect(mergeVariables(initialVariables, newVariables)).toEqual({
      a: ['a1', 'b1', 'c1'],
      b: 'b1',
    });
  });

  it('should merge nested objects', () => {
    const initialVariables = {
      a: {
        z: {
          a1: 'a1',
          a2: 'a2',
        },
      },
      b: 'b',
      c: 'c',
    };
    const newVariables = {
      a: {
        z: {
          a1: 'a11',
          a2: [1, 2, 3],
        },
        t: 't1',
      },
      b: 'b1',
    };

    expect(mergeVariables(initialVariables, newVariables)).toEqual({
      a: {
        z: {
          a1: 'a11',
          a2: [1, 2, 3],
        },
        t: 't1',
      },
      b: 'b1',
    });
  });

  it('should preserve unchanged properties', () => {
    const initialVariables = {
      a: {
        z: {
          a1: 'a1',
          a2: 'a2',
        },
      },
      b: 'b',
      c: 'c',
    };
    const newVariables = {
      a: {
        z: {
          a2: [1, 2, 3],
        },
        t: 't1',
      },
      b: 'b1',
    };

    expect(mergeVariables(initialVariables, newVariables)).toEqual({
      a: {
        z: {
          a1: 'a1',
          a2: [1, 2, 3],
        },
        t: 't1',
      },
      b: 'b1',
    });
  });
});
