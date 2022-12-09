/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {loadVariables} from 'services';
import {LoadingIndicator} from 'components';

import WrappedTable from './Table';
import RawDataTable from './RawDataTable';
import DefaultTable from './DefaultTable';

jest.mock('./processRawData', () => jest.fn().mockReturnValue({}));

jest.mock('./processDefaultData', () =>
  jest.fn().mockReturnValue({head: ['col1', 'col2', {id: 'col3'}]})
);

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([]),
  };
});

beforeEach(() => {
  jest.clearAllMocks();
});

const testDefinition = {key: 'definitionKey', versions: ['ver1'], tenantIds: ['id1']};
const report = {
  reportType: 'process',
  combined: false,
  data: {
    definitions: [testDefinition],
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

it('should show default report', () => {
  const node = shallow(<Table {...props} />);

  expect(node.find(DefaultTable)).toBeDefined();
});

it('should show raw data report', () => {
  const node = shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
    />
  );

  expect(node.find(RawDataTable)).toBeDefined();
});

it('should show loading indicator when loading process variables', () => {
  loadVariables.mockReturnValueOnce([]);
  const node = shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
    />
  );
  runAllEffects();

  expect(node.find(LoadingIndicator)).toBeDefined();
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

  node.find(DefaultTable).prop('updateSorting')('columnId', 'desc');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][1].data.configuration.sorting).toEqual({
    by: 'columnId',
    order: 'desc',
  });
});

it('should pass loaded process variables to raw data handler', () => {
  const variables = [{name: 'foo', type: 'String', label: 'fooLabel'}];
  loadVariables.mockReturnValueOnce(variables);
  const node = shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
    />
  );
  runAllEffects();

  expect(node.find(RawDataTable).prop('processVariables')).toEqual(variables);
});

it('should pass loaded process variables to group by variable handler', () => {
  const variables = [{name: 'foo', type: 'String', label: 'fooLabel'}];
  loadVariables.mockReturnValueOnce(variables);
  const node = shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, groupBy: {type: 'variable'}},
      }}
      formatter={(v) => v}
    />
  );

  runAllEffects();

  expect(node.find(DefaultTable).prop('processVariables')).toEqual(variables);
});

it('should not pass updateSorting to the table component for report grouped by process', () => {
  const spy = jest.fn();
  const node = shallow(<Table {...props} loadReport={spy} updateReport={() => {}} />);

  node.find(DefaultTable).prop('updateSorting')();
  expect(spy).toHaveBeenCalled();

  node.setProps({
    report: {
      ...report,
      data: {...report.data, distributedBy: {type: 'process'}},
      result: {data: []},
    },
  });

  expect(node.find(DefaultTable).prop('updateSorting')).toBe(false);
});
