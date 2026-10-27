/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatters} from 'services';

import {isSuccessful} from './service';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    convertToMilliseconds: jest.fn(),
  },
}));

it('should return correct success state', () => {
  expect(isSuccessful({value: '100', target: '200', isBelow: true})).toBe(true);
  expect(isSuccessful({value: '100', target: '200', isBelow: false})).toBe(false);
  expect(isSuccessful({value: '22', target: '11', isBelow: true})).toBe(false);
  expect(isSuccessful({value: '11', target: '22', isBelow: true})).toBe(true);

  formatters.convertToMilliseconds.mockReturnValueOnce(100);
  expect(
    isSuccessful({value: '11', target: '10', isBelow: true, measure: 'duration', unit: 'days'})
  ).toBe(true);
  expect(formatters.convertToMilliseconds).toHaveBeenCalledWith('10', 'days');
});
