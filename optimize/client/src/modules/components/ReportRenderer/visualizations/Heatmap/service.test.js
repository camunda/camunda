/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {calculateTargetValueHeat, getConfig} from './service';

jest.mock('heatmap.js', () => {});
jest.mock('services', () => ({
  formatters: {convertToMilliseconds: (value) => value},
}));

describe('calculateTargetValueHeat', () => {
  it('should return the relative difference between actual and target value', () => {
    expect(calculateTargetValueHeat({a: 10}, {a: {value: 5, unit: 'millis'}})).toEqual({a: 1});
  });

  it('should return null for an element that is below target value', () => {
    expect(calculateTargetValueHeat({a: 2}, {a: {value: 5, unit: 'millis'}})).toEqual({a: null});
  });
});

it('should construct rawdata report with the target value as a filter', () => {
  const configuration = {
    heatmapTargetValue: {
      values: {
        flowNodeA: {
          value: 1234,
          unit: 'days',
        },
      },
    },
  };

  expect(
    getConfig(
      {
        configuration,
        definitions: [
          {
            key: '1',
            versions: ['1'],
            tenantIds: ['tenantA'],
          },
        ],
        filter: [{type: 'test'}],
      },
      'flowNodeA'
    )
  ).toMatchSnapshot();
});
