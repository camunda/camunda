/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {evaluateReport, processResult} from './reportService';

import {post} from 'request';

jest.mock('request', () => {
  const rest = jest.requireActual('request');

  return {
    ...rest,
    post: jest.fn(),
  };
});

it('should process duration reports', () => {
  expect(
    processResult({
      data: {
        groupBy: {},
        view: {
          properties: ['duration'],
          entity: 'processInstance',
        },
        configuration: {},
      },
      result: {
        type: 'map',
        data: [
          {key: '2015-03-25T12:00:00Z', value: 4},
          {key: '2015-03-26T12:00:00Z', value: 8},
        ],
      },
    })
  ).toEqual({
    data: [
      {key: '2015-03-25T12:00:00Z', value: 4},
      {key: '2015-03-26T12:00:00Z', value: 8},
    ],
    type: 'map',
  });
});

it('should add a label to data with variable value key "missing"', () => {
  expect(
    processResult({
      result: {
        data: [{key: 'missing', value: 5}],
      },
      data: {
        configuration: {xml: 'fooXml'},
        groupBy: {
          type: 'variable',
          value: '',
        },
        view: {properties: ['']},
      },
    })
  ).toEqual({
    data: [{key: 'missing', value: 5, label: 'null / undefined'}],
  });
});

it('should convert group by none distribute by process hypermap report to normal map report', async () => {
  const hyperMapReport = {
    data: {
      groupBy: {type: 'none'},
      distributedBy: {type: 'process'},
    },
    result: {
      type: 'hyperMap',
      measures: [
        {
          type: 'hyperMap',
          data: [
            {
              key: '____none',
              label: '____none',
              value: [
                {key: 'definition1', value: 12, label: 'Definition 1'},
                {key: 'definition2', value: 34, label: 'Definition 2'},
              ],
            },
          ],
        },
      ],
    },
  };

  post.mockReturnValueOnce({json: () => hyperMapReport});

  expect(await evaluateReport()).toEqual({
    data: {
      groupBy: {type: 'none'},
      distributedBy: {type: 'process'},
    },
    result: {
      type: 'map',
      measures: [
        {
          type: 'map',
          data: [
            {key: 'definition1', value: 12, label: 'Definition 1'},
            {key: 'definition2', value: 34, label: 'Definition 2'},
          ],
        },
      ],
    },
  });
});
