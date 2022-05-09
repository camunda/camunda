/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
