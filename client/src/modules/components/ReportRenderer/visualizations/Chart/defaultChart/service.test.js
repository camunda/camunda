/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDurationFormattingOptions, getFormattedTargetValue} from './service';
import {formatters} from 'services';

const {convertToMilliseconds} = formatters;

jest.mock('services', () => {
  return {
    formatters: {convertToMilliseconds: jest.fn()}
  };
});

it('should show nice ticks for duration formats on the y axis', () => {
  const maxValue = 7 * 24 * 60 * 60 * 1000;

  const config = createDurationFormattingOptions(0, maxValue);

  expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
  expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('3d');
});

it('should set LineAt option to target value if it is active', () => {
  const value = getFormattedTargetValue({value: 10});
  expect(value).toBe(10);
});

it('should invoke convertToMilliSeconds when target value is set to Date Format', () => {
  getFormattedTargetValue({value: 10, unit: 'millis'});
  expect(convertToMilliseconds).toBeCalledWith(10, 'millis');
});
