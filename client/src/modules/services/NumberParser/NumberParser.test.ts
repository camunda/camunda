/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as numberParser from './NumberParser';

it('should check for valid float number', () => {
  expect(numberParser.isFloatNumber('123')).toBe(true);
  expect(numberParser.isFloatNumber('123.')).toBe(true);
  expect(numberParser.isFloatNumber('123.231')).toBe(true);
  expect(numberParser.isFloatNumber('+123')).toBe(true);
  expect(numberParser.isFloatNumber('-123.123')).toBe(true);
  expect(numberParser.isFloatNumber('123.a')).toBe(false);
  expect(numberParser.isFloatNumber('as.12')).toBe(false);
  expect(numberParser.isFloatNumber('-323e8')).toBe(true);
  expect(numberParser.isFloatNumber('12.233.3')).toBe(false);
});

it('should check for positive number', () => {
  expect(numberParser.isPositiveNumber('+123')).toBe(true);
  expect(numberParser.isPositiveNumber('123')).toBe(true);
  expect(numberParser.isPositiveNumber('123.123')).toBe(true);
  expect(numberParser.isPositiveNumber('-123')).toBe(false);
});

describe('isNonNegativeNumber', () => {
  it('should return false if the passed string contains letters', () => {
    expect(numberParser.isNonNegativeNumber('123h')).toBe(false);
  });

  it('should return false if the passed string is negative number', () => {
    expect(numberParser.isNonNegativeNumber('-1')).toBe(false);
  });

  it('should return true if a possitve string number is passed', () => {
    expect(numberParser.isNonNegativeNumber('1')).toBe(true);
  });

  it('should return true if a number is passed', () => {
    expect(numberParser.isNonNegativeNumber(1)).toBe(true);
  });
});
