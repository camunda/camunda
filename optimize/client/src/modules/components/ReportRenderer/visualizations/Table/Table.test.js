/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {loadVariables} from 'services';

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

const variableReport = update(report, {
  data: {
    view: {$set: {entity: 'variable', properties: ['rawData']}},
    filter: {$set: [{type: 'runningInstancesOnly'}]},
    definitions: {$set: [{key: 'aKey', versions: ['1'], tenantIds: ['tenantId']}]},
  },
  result: {
    measures: {
      $set: [{data: 123, aggregationType: {type: 'avg', value: null}, property: {}}],
    },
  },
});

const Table = WrappedTable.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  report,
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should show default report', () => {
  const node = shallow(<Table {...props} />);

  expect(node.find(DefaultTable)).toExist();
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

  expect(node.find(RawDataTable)).toExist();
});

it('should enable table loading when loading process variables', () => {
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

  expect(node.find(RawDataTable).prop('processVariables')).toBe(undefined);
  expect(node.find(RawDataTable).prop('loading')).toBe(true);
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

it('should call loadVariables for process variable report', () => {
  shallow(<Table {...props} report={variableReport} />);
  runAllEffects();

  expect(loadVariables).toHaveBeenCalledWith({
    processesToQuery: [
      {
        processDefinitionKey: 'aKey',
        processDefinitionVersions: ['1'],
        tenantIds: ['tenantId'],
      },
    ],
    filter: [{type: 'runningInstancesOnly'}],
  });
});
