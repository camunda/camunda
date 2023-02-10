/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  processResult,
  getReportResult,
  isCategoricalBar,
  isCategorical,
  isTextReportValid,
  isTextReportTooLong,
} from './reportService';

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

describe('getReportResult', () => {
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

    expect(await getReportResult(hyperMapReport)).toEqual({
      data: [
        {key: 'definition1', value: 12, label: 'Definition 1'},
        {key: 'definition2', value: 34, label: 'Definition 2'},
      ],
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
    });
  });

  it('default missing value to empty array', async () => {
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
            data: [],
          },
        ],
      },
    };

    post.mockReturnValueOnce({json: () => hyperMapReport});

    expect(await getReportResult(hyperMapReport)).toEqual({
      data: [],
      type: 'map',
      measures: [
        {
          type: 'map',
          data: [],
        },
      ],
    });
  });
});

describe('isCategoricalBar', () => {
  it('should return false if the visualization is not a bar chart', () => {
    expect(
      isCategoricalBar({
        visualization: 'line',
        configuration: {xml: 'fooXml'},
        groupBy: {
          type: 'variable',
          value: {type: 'String'},
        },
      })
    ).toBe(false);
  });

  it('should return false if the grouping is not categorical', () => {
    expect(
      isCategoricalBar({
        visualization: 'bar',
        configuration: {xml: 'fooXml'},
        groupBy: {
          type: 'startDate',
        },
      })
    ).toBe(false);
  });

  it('should return true for categorical bar chart reports', () => {
    expect(
      isCategoricalBar({
        visualization: 'bar',
        configuration: {xml: 'fooXml'},
        groupBy: {
          type: 'variable',
          value: {type: 'String'},
        },
      })
    ).toBe(true);
  });
});

describe('isCategorical', () => {
  it('should return true for flow node groupBy', () => {
    expect(
      isCategorical({
        groupBy: {
          type: 'flowNodes',
        },
      })
    ).toBe(true);
  });

  it('should return false for double variable groupBy', () => {
    expect(
      isCategorical({
        groupBy: {
          type: 'variabe',
          value: {type: 'Double'},
        },
      })
    ).toBe(false);
  });

  it('should return true for group by assignee', () => {
    expect(
      isCategorical({
        groupBy: {
          type: 'assignee',
        },
      })
    ).toBe(true);
  });

  it('should return true for distributed by process', () => {
    expect(
      isCategorical({
        distributedBy: {
          type: 'process',
        },
      })
    ).toBe(true);
  });
});

describe('isTextReportValid', () => {
  it('should return true if report is valid', () => {
    expect(isTextReportValid(100)).toBe(true);
  });

  it('should return false if report is not valid', () => {
    expect(isTextReportValid(0)).toBe(false);
    expect(isTextReportValid(3001)).toBe(false);
  });
});

describe('isTextReportTooLong', () => {
  it('should return true if report is too long', () => {
    expect(isTextReportTooLong(3001)).toBe(true);
  });

  it('should return false if report is not too long', () => {
    expect(isTextReportTooLong(100)).toBe(false);
  });
});
