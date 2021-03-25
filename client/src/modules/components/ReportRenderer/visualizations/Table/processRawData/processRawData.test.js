/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {NoDataNotice} from 'components';

import processRawData from './processRawData';

const data = {
  configuration: {
    tableColumns: {
      includeNewVariables: true,
      includedColumns: [],
      excludedColumns: [],
      columnOrder: [],
    },
  },
};

const result = {
  data: [
    {
      processInstanceId: 'foo',
      processDefinitionId: 'bar',
      variables: {
        var1: 12,
        var2: null,
      },
    },
    {
      processInstanceId: 'xyz',
      processDefinitionId: 'abc',
      variables: {
        var1: null,
        var2: true,
      },
    },
  ],
};

it('should transform data to table compatible format', () => {
  expect(processRawData({report: {data, result}})).toMatchSnapshot();
});

it('should not include columns that are hidden', () => {
  const data = {
    configuration: {
      tableColumns: {
        includeNewVariables: false,
        includedColumns: ['processInstanceId'],
        excludedColumns: ['processDefinitionId', 'variable:var1', 'variable:var1'],
        columnOrder: [],
      },
    },
  };
  expect(processRawData({report: {data, result}})).toEqual({
    body: [['foo'], ['xyz']],
    head: [{id: 'processInstanceId', label: 'Process Instance Id', title: 'Process Instance Id'}],
  });
});

it('should exclude variable columns using the variable prefix', () => {
  const data = {
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: ['variable:var1'],
        columnOrder: [],
      },
    },
  };
  expect(processRawData({report: {data, result}})).toMatchSnapshot();
});

it('should make the processInstanceId a link', () => {
  const cell = processRawData(
    {
      report: {
        result: {data: [{processInstanceId: '123', engineName: '1', variables: {}}]},
        data,
      },
    },
    {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  ).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/123');
});

it('should format start and end dates', () => {
  // using format here to dynamically return date with client timezone
  const startDate = format(parseISO('2019-06-07'), 'yyyy-MM-dd');
  const endDate = format(parseISO('2019-06-09'), 'yyyy-MM-dd');

  const cells = processRawData({
    report: {
      result: {
        data: [
          {
            startDate,
            endDate,
            variables: {},
          },
        ],
      },
      data,
    },
  }).body[0];

  const expectedDateFormat = "yyyy-MM-dd HH:mm:ss 'UTC'X";
  expect(cells[0]).toBe(format(parseISO(startDate), expectedDateFormat));
  expect(cells[1]).toBe(format(parseISO(endDate), expectedDateFormat));
});

it('should format duration', () => {
  const cells = processRawData({
    report: {
      result: {
        data: [
          {
            duration: 123023423,
            variables: {},
          },
        ],
      },
      data,
    },
  }).body[0];

  expect(cells[0]).toBe('1d 10h 10min 23s 423ms');
});

it('should not make the processInstanceId a link if no endpoint is specified', () => {
  const cell = processRawData({
    report: {
      result: {data: [{processInstanceId: '123', engineName: '1', variables: {}}]},
      data,
    },
  }).body[0][0];

  expect(cell).toBe('123');
});

it('should show no data message when all column are excluded', () => {
  const data = {
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: [
          'processInstanceId',
          'processDefinitionId',
          'variable:var1',
          'variable:var2',
        ],
      },
    },
  };
  expect(processRawData({report: {data, result}})).toEqual({
    body: [],
    head: [],
    noData: <NoDataNotice type="info">You need to enable at least one table column</NoDataNotice>,
  });
});

it('should not crash for empty results', () => {
  expect(processRawData({report: {data, result: {data: []}}})).toEqual({
    body: [],
    head: [],
  });
});
