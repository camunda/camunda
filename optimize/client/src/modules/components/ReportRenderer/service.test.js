/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatters} from 'services';

import {formatValue, getFormatter} from './service';

jest.mock('services', () => ({
  formatters: {
    frequency: jest.fn((value) => value),
    duration: jest.fn((value) => value),
    percentage: jest.fn((value) => value),
    compact: jest.fn((value) => value),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

it('should forward the shortNotation flag to the duration formatter', () => {
  formatValue(5000, 'duration', 2, true);

  expect(formatters.duration).toHaveBeenCalledWith(5000, 2, true);
});

it('should default shortNotation to undefined when not provided', () => {
  formatValue(5000, 'duration', 2);

  expect(formatters.duration).toHaveBeenCalledWith(5000, 2, undefined);
});

it('should pass the extra argument harmlessly to non-duration formatters', () => {
  formatValue(42, 'frequency', 0, true);

  // the frequency formatter ignores the trailing argument; passing it must not change behaviour
  expect(formatters.frequency).toHaveBeenCalledWith(42, 0, true);
});

it('should resolve the formatter by measure name', () => {
  expect(getFormatter('duration')).toBe(formatters.duration);
  expect(getFormatter('percentage')).toBe(formatters.percentage);
  expect(getFormatter('compact')).toBe(formatters.compact);
});
