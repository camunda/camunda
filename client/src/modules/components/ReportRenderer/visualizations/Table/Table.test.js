/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import WrappedTable from './Table';
import processRawData from './processRawData';

import {getWebappEndpoints} from 'config';

jest.mock('./processRawData', () => ({
  process: jest.fn().mockReturnValue({}),
  decision: jest.fn().mockReturnValue({}),
}));

jest.mock('./processDefaultData', () =>
  jest.fn().mockReturnValue({head: ['col1', 'col2', {id: 'col3'}]})
);

jest.mock('config', () => ({getWebappEndpoints: jest.fn()}));

const report = {
  reportType: 'process',
  combined: false,
  data: {
    groupBy: {
      value: {},
      type: '',
    },
    view: {properties: ['duration']},
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: [],
        columnOrder: ['col1', 'col2', 'col3'],
      },
    },
    visualization: 'table',
  },
  result: {
    instanceCount: 5,
    data: [],
  },
};

const Table = WrappedTable.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  report,
};

it('should get the camunda endpoints for raw data', () => {
  getWebappEndpoints.mockClear();
  shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
    />
  );
  runLastEffect();

  expect(getWebappEndpoints).toHaveBeenCalled();
});

it('should not get the camunda endpoints for non-raw-data tables', () => {
  getWebappEndpoints.mockClear();
  shallow(<Table {...props} report={{...report, result: {data: []}}} />);

  expect(getWebappEndpoints).not.toHaveBeenCalled();
});

it('should process raw data', async () => {
  await shallow(
    <Table
      {...props}
      report={{
        ...report,
        result: {
          data: [
            {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
            {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}},
          ],
        },
      }}
      formatter={(v) => v}
    />
  );
  runLastEffect();

  expect(processRawData.process).toHaveBeenCalled();
});

it('should load report when updating sorting', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...props}
      report={{...report, result: {data: []}}}
      loadReport={spy}
      updateReport={() => {}}
    />
  );

  node.find('Table').prop('updateSorting')('columnId', 'desc');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][1].data.configuration.sorting).toEqual({
    by: 'columnId',
    order: 'desc',
  });
});

it('should reload report with correct pagination parameters', async () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...props}
      loadReport={spy}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
    />
  );
  runLastEffect();

  node.find('Table').prop('fetchData')({pageIndex: 2, pageSize: 50});
  expect(spy).toHaveBeenCalledWith({limit: 50, offset: 100});
});

it('should update configuration when arranging columns', async () => {
  const spy = jest.fn();
  const node = shallow(<Table {...props} updateReport={spy} />);

  runLastEffect();

  node.find('ColumnRearrangement').prop('onChange')(0, 2);

  expect(spy).toHaveBeenCalledWith({
    configuration: {tableColumns: {columnOrder: {$set: ['col2', 'col3', 'col1']}}},
  });
});
