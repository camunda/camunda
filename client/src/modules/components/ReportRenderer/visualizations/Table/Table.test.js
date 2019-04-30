/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import WrappedTable from './Table';
import processRawData from './processRawData';

import {getCamundaEndpoints} from './service';

jest.mock('./processRawData', () => ({
  process: jest.fn(),
  decision: jest.fn()
}));

jest.mock('./service', () => {
  const rest = jest.requireActual('./service');
  return {
    ...rest,
    getCamundaEndpoints: jest.fn().mockReturnValue('camundaEndpoint')
  };
});

const report = {
  reportType: 'process',
  combined: false,
  data: {
    groupBy: {
      value: {},
      type: ''
    },
    view: {property: 'duration'},
    configuration: {
      excludedColumns: []
    },
    visualization: 'table'
  },
  result: {
    processInstanceCount: 5,
    data: []
  }
};

const Table = WrappedTable.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  report
};

it('should get the camunda endpoints for raw data', () => {
  getCamundaEndpoints.mockClear();
  shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {property: 'rawData'}},
        result: {data: [1, 2, 3]}
      }}
    />
  );

  expect(getCamundaEndpoints).toHaveBeenCalled();
});

it('should not get the camunda endpoints for non-raw-data tables', () => {
  getCamundaEndpoints.mockClear();
  shallow(<Table {...props} report={{...report, result: {data: []}}} />);

  expect(getCamundaEndpoints).not.toHaveBeenCalled();
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
            {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
          ]
        }
      }}
      formatter={v => v}
    />
  );

  expect(processRawData.process).toHaveBeenCalled();
});

it('should display an error message for a non-object result (single number)', async () => {
  const node = await shallow(
    <Table
      {...props}
      report={{
        ...report,
        result: {data: 7}
      }}
      errorMessage="Error"
      formatter={v => v}
    />
  );

  expect(node.find('ReportBlankSlate')).toExist();
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toBe('Error');
});

it('should display an error message if no data is provided', async () => {
  const node = await shallow(
    <Table {...props} report={{...report, result: null}} errorMessage="Error" formatter={v => v} />
  );

  expect(node.find('ReportBlankSlate')).toExist();
  expect(node.find('ReportBlankSlate').prop('errorMessage')).toBe('Error');
});

it('should not display an error message if data is valid', async () => {
  const node = await shallow(
    <Table
      {...props}
      report={{
        ...report,
        result: {
          data: [
            {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
            {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
          ]
        }
      }}
      errorMessage="Error"
      formatter={v => v}
    />
  );

  expect(node.find('ReportBlankSlate')).not.toExist();
});

it('should set the correct parameters when updating sorting', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table {...props} report={{...report, result: {data: []}}} updateReport={spy} />
  );

  node.instance().updateSorting('columnId', 'desc');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].parameters.sorting).toEqual({$set: {by: 'columnId', order: 'desc'}});
});
