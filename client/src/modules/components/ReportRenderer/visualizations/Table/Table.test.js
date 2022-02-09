/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {loadVariables} from 'services';

import WrappedTable from './Table';
import processRawData from './processRawData';
import processDefaultData from './processDefaultData';
import {getWebappEndpoints} from 'config';
import ObjectVariableModal from './ObjectVariableModal';

jest.mock('./processRawData', () => jest.fn().mockReturnValue({}));

jest.mock('./processDefaultData', () =>
  jest.fn().mockReturnValue({head: ['col1', 'col2', {id: 'col3'}]})
);

jest.mock('config', () => ({getWebappEndpoints: jest.fn()}));

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([]),
  };
});

beforeEach(() => {
  processRawData.mockClear();
  processDefaultData.mockClear();
  loadVariables.mockClear();
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
  runAllEffects();

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
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
      formatter={(v) => v}
    />
  );
  runAllEffects();

  expect(processRawData).toHaveBeenCalled();
});

it('should display object variable modal when invoked from processRawData function', async () => {
  const node = await shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
      formatter={(v) => v}
    />
  );
  runAllEffects();

  processRawData.mock.calls[0][0].onVariableView('varName', 'instanceId', testDefinition.key);

  expect(node.find(ObjectVariableModal).prop('variable')).toEqual({
    name: 'varName',
    processInstanceId: 'instanceId',
    processDefinitionKey: testDefinition.key,
    versions: testDefinition.versions,
    tenantIds: testDefinition.tenantIds,
  });

  node.find(ObjectVariableModal).simulate('close');
  expect(node.find(ObjectVariableModal)).not.toExist();
});

it('should close object variable modal', async () => {
  const node = await shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 20}},
      }}
      formatter={(v) => v}
    />
  );
  runAllEffects();

  processRawData.mock.calls[0][0].onVariableView('varName', 'instanceId', testDefinition.key);

  node.find(ObjectVariableModal).simulate('close');
  expect(node.find(ObjectVariableModal)).not.toExist();
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

it('should reload report with correct pagination parameters', () => {
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
  runAllEffects();

  node.find('Table').prop('fetchData')({pageIndex: 2, pageSize: 50});
  expect(spy).toHaveBeenCalledWith({limit: 50, offset: 100});
});

it('should show an error when loading more than the first 10.000 instances', async () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...props}
      loadReport={spy}
      report={{
        ...report,
        data: {...report.data, view: {properties: ['rawData']}},
        result: {data: [1, 2, 3], pagination: {limit: 1000}},
      }}
    />
  );
  runAllEffects();

  await node.find('Table').prop('fetchData')({pageIndex: 11, pageSize: 1000});
  expect(node.find('Table').prop('error')).toBeDefined();
  expect(spy).not.toHaveBeenCalled();
});

it('should update configuration when arranging columns', () => {
  const spy = jest.fn();
  const node = shallow(<Table {...props} updateReport={spy} />);

  runAllEffects();

  node.find('ColumnRearrangement').prop('onChange')(0, 2);

  expect(spy).toHaveBeenCalledWith({
    configuration: {tableColumns: {columnOrder: {$set: ['col2', 'col3', 'col1']}}},
  });
});

it('should pass loaded process variables to raw data handler', async () => {
  const variables = [{name: 'foo', type: 'String', label: 'fooLabel'}];
  loadVariables.mockReturnValueOnce(variables);
  await shallow(
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

  expect(processRawData.mock.calls[0][0].processVariables).toEqual(variables);
});

it('should pass loaded process variables to group by variable handler', async () => {
  const variables = [{name: 'foo', type: 'String', label: 'fooLabel'}];
  loadVariables.mockReturnValueOnce(variables);
  await shallow(
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

  expect(processDefaultData.mock.calls[0][1]).toEqual(variables);
});
