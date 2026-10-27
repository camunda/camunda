/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import processHyperData from './processHyperData';

import {getFormattedLabels, getBodyRows} from './service';

jest.mock('./service', () => ({
  getHyperTableProps: jest.fn().mockReturnValue({
    labels: [['key label', 'operation label']],
    combinedResult: [[]],
  }),
  getFormattedLabels: jest.fn().mockReturnValue([
    {label: 'Report A', columns: ['value', 'Relative Frequency']},
    {label: 'Report B', columns: ['value', 'Relative Frequency']},
  ]),
  getBodyRows: jest.fn().mockReturnValue([
    ['a', 1, '12.3%', 1, '12.3%'],
    ['b', 2, '12.3%', 2, '12.3%'],
    ['c', 3, '12.3%', 3, '12.3%'],
  ]),
  sortColumns: (head, body) => ({sortedHead: head, sortedBody: body}),
  formatLabelsForTableBody: (body) => body,
}));

const singleReport = {
  data: {
    view: {properties: ['frequency']},
    groupBy: {type: 'none'},
  },
};

const hyperReport = {
  hyper: true,
  data: {
    reports: [{id: 'reportA'}, {id: 'reportB'}],
    configuration: {tableColumns: {columnOrder: []}},
  },
  result: {
    data: {
      reportA: {
        name: 'Report A',
        ...singleReport,
      },
      reportB: {
        name: 'Report B',
        ...singleReport,
      },
    },
  },
};

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  fomatter: (v) => v,
  report: hyperReport,
};

it('should return correct labels and body when combining two table report', async () => {
  expect(processHyperData(props)).toEqual({
    head: [
      {id: 'key label', label: ' ', columns: ['key label']},
      {label: 'Report A', columns: ['value', 'Relative Frequency']},
      {label: 'Report B', columns: ['value', 'Relative Frequency']},
    ],
    body: [
      ['a', 1, '12.3%', 1, '12.3%'],
      ['b', 2, '12.3%', 2, '12.3%'],
      ['c', 3, '12.3%', 3, '12.3%'],
    ],
  });
});

it('should not include a column in a hyper report if it is hidden in the configuration', async () => {
  getFormattedLabels.mockReturnValue([
    {label: 'Report A', columns: ['value']},
    {label: 'Report B', columns: ['value']},
  ]);
  getBodyRows.mockReturnValue([
    ['a', 1, 1],
    ['b', 2, 2],
    ['c', 3, 3],
  ]);

  expect(processHyperData(props)).toEqual({
    head: [
      {id: 'key label', label: ' ', columns: ['key label']},
      {label: 'Report A', columns: ['value']},
      {label: 'Report B', columns: ['value']},
    ],
    body: [
      ['a', 1, 1],
      ['b', 2, 2],
      ['c', 3, 3],
    ],
  });
});
