/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import createHyperChartOptions from './createHyperChartOptions';
import {createBarOptions} from '../defaultChart/createDefaultChartOptions';

jest.mock('../defaultChart/createDefaultChartOptions', () => ({
  createBarOptions: jest.fn(),
}));

it('should find max duration for hyper reports', () => {
  const maxDuration = 99999999999;

  createHyperChartOptions({
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

  createHyperChartOptions({
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
