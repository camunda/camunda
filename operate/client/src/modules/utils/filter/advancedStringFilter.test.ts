/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  advancedStringFilterCodec,
  encodeFilterOperation,
  splitEncodedFilterOperation,
} from './advancedStringFilter';

describe('encodeFilterOperation', () => {
  it('should strip the $ prefix from the operator', () => {
    expect(encodeFilterOperation('$eq', 'order-123')).toBe('eq_order-123');
    expect(encodeFilterOperation('$like', 'order')).toBe('like_order');
  });
});

describe('splitEncodedFilterOperation', () => {
  it('should split on the first separator and restore the $ prefix', () => {
    expect(splitEncodedFilterOperation('eq_order_123')).toEqual({
      operator: '$eq',
      value: 'order_123',
    });
  });

  it('should return null when no separator is present', () => {
    expect(splitEncodedFilterOperation('eqorder')).toBeNull();
  });

  it('should return null for an unknown operator slug', () => {
    expect(splitEncodedFilterOperation('foo_value')).toBeNull();
  });
});

describe('advancedStringFilterCodec.decode', () => {
  it('should decode basic filter values into their advanced string filter', () => {
    expect(advancedStringFilterCodec.decode('eq_order-123')).toEqual({
      $eq: 'order-123',
    });
    expect(advancedStringFilterCodec.decode('neq_order-123')).toEqual({
      $neq: 'order-123',
    });

    expect(advancedStringFilterCodec.decode('exists_true')).toEqual({
      $exists: true,
    });
    expect(advancedStringFilterCodec.decode('exists_false')).toEqual({
      $exists: false,
    });
  });

  it('should decode like filters with wildcards applied', () => {
    expect(advancedStringFilterCodec.decode('like_order')).toEqual({
      $like: '*order*',
    });
  });

  it('should decode list filters by splitting on whitespace and commas', () => {
    expect(advancedStringFilterCodec.decode('in_a, b c')).toEqual({
      $in: ['a', 'b', 'c'],
    });
    expect(advancedStringFilterCodec.decode('notIn_a, b c')).toEqual({
      $notIn: ['a', 'b', 'c'],
    });
  });

  it('should split on the first separator so values may contain underscores', () => {
    expect(advancedStringFilterCodec.decode('eq_a_b_c')).toEqual({
      $eq: 'a_b_c',
    });
  });

  it('should decode multiple chained filter entries', () => {
    expect(
      advancedStringFilterCodec.decode('eq_order-123___exists_true'),
    ).toEqual({
      $eq: 'order-123',
      $exists: true,
    });
  });

  it('should fail on invalid inputs', () => {
    expect(advancedStringFilterCodec.safeDecode('order-123').success).toBe(
      false,
    );
    expect(advancedStringFilterCodec.safeDecode('foo_value').success).toBe(
      false,
    );
    expect(advancedStringFilterCodec.safeDecode('exists_maybe').success).toBe(
      false,
    );
    expect(advancedStringFilterCodec.safeDecode('in_   ').success).toBe(false);
  });
});

describe('advancedStringFilterCodec.encode', () => {
  it('should encode filters with basic conditions', () => {
    expect(advancedStringFilterCodec.encode({$eq: 'order-123'})).toBe(
      'eq_order-123',
    );
    expect(advancedStringFilterCodec.encode({$neq: 'order-123'})).toBe(
      'neq_order-123',
    );
    expect(advancedStringFilterCodec.encode({$exists: true})).toBe(
      'exists_true',
    );
  });

  it('should encode $like filter by stripping the wildcards added on decode', () => {
    expect(advancedStringFilterCodec.encode({$like: '*ord*er*'})).toBe(
      'like_ord*er',
    );
  });

  it('should encode list filters', () => {
    expect(advancedStringFilterCodec.encode({$in: ['a', 'b', 'c']})).toBe(
      'in_a b c',
    );
  });

  it('should encode filters with multiple conditions', () => {
    expect(
      advancedStringFilterCodec.encode({
        $eq: 'order-123',
        $exists: true,
        $in: ['a', 'b', 'c'],
        $like: '*order*',
      }),
    ).toBe('eq_order-123___exists_true___in_a b c___like_order');
  });
});
