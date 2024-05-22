/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import createCombinedChartOptions from './createCombinedChartOptions';
import {createBarOptions} from '../defaultChart/createDefaultChartOptions';

jest.mock('../defaultChart/createDefaultChartOptions', () => ({
  createBarOptions: jest.fn(),
}));

it('should find max duration for combined reports', () => {
  const maxDuration = 99999999999;

  createCombinedChartOptions({
    report: {
      data: {visualization: 'pie', configuration: {}},
      result: {
        data: {
          report1: {
            data: {
              view: {entity: 'flowNode', properties: ['duration']},
              groupBy: '',
            },
            result: {data: 13719846575},
          },
          report2: {
            data: {
              view: {entity: 'flowNode', properties: ['duration']},
              groupBy: '',
            },
            result: {data: maxDuration},
          },
        },
      },
    },
  });

  expect(createBarOptions.mock.calls[0][0].maxDuration).toBe(maxDuration);
});

it('should find max duration for multi measure reports', () => {
  const maxDuration = 99999999999;
  const measures = [
    {
      property: 'duration',
      data: [{key: 'flowNode1', value: [{key: 'assignee1', value: 5454545}]}],
    },
    {
      property: 'duration',
      data: [
        {
          key: 'flowNode1',
          value: [
            {key: 'assignee1', value: maxDuration},
            {key: 'assignee2', value: 2323},
          ],
        },
        {key: 'flowNode2', value: [{key: 'assignee1', value: 234323}]},
      ],
    },
  ];

  createCombinedChartOptions({
    report: {
      data: {visualization: 'pie', configuration: {}},
      result: {
        data: {
          report1: {data: {view: {entity: 'flowNode', properties: ['duration']}, groupBy: ''}},
        },
        measures,
      },
    },
  });

  expect(createBarOptions.mock.calls[0][0].maxDuration).toBe(maxDuration);
});
