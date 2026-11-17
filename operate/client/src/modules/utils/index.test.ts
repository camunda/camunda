/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

import {isValidJSON, safeJsonParse} from './index';

describe('safeJsonParse', () => {
  it('should return the parsed JSON when no schema is provided', () => {
    expect(safeJsonParse('{"foo":"bar"}')).toEqual({foo: 'bar'});
  });

  it('should return undefined for invalid JSON', () => {
    expect(safeJsonParse('{foo:"bar"}')).toBeUndefined();
  });

  it('should return schema-parsed data when a schema is provided', () => {
    const schema = z.object({count: z.coerce.number()});

    const parsed = safeJsonParse('{"count":"3"}', schema);

    expect(parsed).toEqual({count: 3});
  });

  it('should return undefined when schema parsing fails', () => {
    const schema = z.object({count: z.number()});

    const parsed = safeJsonParse('{"count":"not a number"}', schema);

    expect(parsed).toBeUndefined();
  });
});

describe('isValidJSON', () => {
  it('should return true for valid JSON', () => {
    expect(isValidJSON('{"foo":"bar"}')).toBe(true);
  });

  it('should return false for invalid JSON', () => {
    expect(isValidJSON('{foo:"bar"}')).toBe(false);
  });
});
