/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
