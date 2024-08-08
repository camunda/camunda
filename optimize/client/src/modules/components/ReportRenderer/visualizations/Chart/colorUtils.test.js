/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {determineBarColor} from './colorsUtils';

it('should return red color for all bars below a target value', () => {
  const data = [
    {key: 'foo', value: 123},
    {key: 'bar', value: 5},
  ];
  const value = determineBarColor(
    {
      isBelow: false,
      value: '10',
    },
    data,
    'testColor'
  );
  expect(value).toEqual(['testColor', '#A62A31']);
});
