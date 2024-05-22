/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
