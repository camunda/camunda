/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getFormattedTargetValue} from './service';
import {formatters} from 'services';

const {convertToMilliseconds} = formatters;

jest.mock('services', () => {
  return {
    formatters: {convertToMilliseconds: jest.fn()},
  };
});

it('should set LineAt option to target value if it is active', () => {
  const value = getFormattedTargetValue({value: 10});
  expect(value).toBe(10);
});

it('should invoke convertToMilliSeconds when target value is set to Date Format', () => {
  getFormattedTargetValue({value: 10, unit: 'millis'});
  expect(convertToMilliseconds).toBeCalledWith(10, 'millis');
});
