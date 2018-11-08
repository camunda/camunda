import React from 'react';
import {shallow} from 'enzyme';

import WrappedTable from './Table';
import {processRawData} from 'services';

import {getCamundaEndpoints, getRelativeValue} from './service';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    processRawData: jest.fn()
  };
});

jest.mock('./service', () => {
  return {
    getCamundaEndpoints: jest.fn().mockReturnValue('camundaEndpoint'),
    getRelativeValue: jest.fn(),
    uniteResults: jest.fn().mockReturnValue([
      {
        a: 1,
        b: 2,
        c: 3
      },
      {
        a: 1,
        b: 2,
        c: 3
      }
    ]),
    getFormattedLabels: jest
      .fn()
      .mockReturnValue([
        {label: 'Report A', columns: ['value', 'Relative Frequency']},
        {label: 'Report B', columns: ['value', 'Relative Frequency']}
      ]),
    getBodyRows: jest
      .fn()
      .mockReturnValue([
        ['a', 1, '12.3%', 1, '12.3%'],
        ['b', 2, '12.3%', 2, '12.3%'],
        ['c', 3, '12.3%', 3, '12.3%']
      ])
  };
});

const Table = WrappedTable.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: ''
};

it('should get the camunda endpoints for raw data', () => {
  getCamundaEndpoints.mockClear();
  shallow(<Table {...props} configuration={{}} data={[{}]} />);

  expect(getCamundaEndpoints).toHaveBeenCalled();
});

it('should not get the camunda endpoints for non-raw-data tables', () => {
  getCamundaEndpoints.mockClear();
  shallow(<Table {...props} configuration={{}} data={{}} />);

  expect(getCamundaEndpoints).not.toHaveBeenCalled();
});

it('should display data for key-value pairs', async () => {
  const node = await shallow(
    <Table
      {...props}
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => v}
      configuration={{}}
    />
  );

  expect(node.find('Table').prop('body')).toEqual([['a', 1], ['b', 2], ['c', 3]]);
});

it('should display the relative percentage for frequency views', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const node = shallow(
    <Table
      {...props}
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => v}
      configuration={{}}
      labels={['key', 'value', 'relative']}
      property="frequency"
      processInstanceCount={5}
    />
  );

  expect(getRelativeValue).toHaveBeenCalledWith(1, 5);
  expect(getRelativeValue).toHaveBeenCalledWith(2, 5);
  expect(getRelativeValue).toHaveBeenCalledWith(3, 5);

  expect(node.find('Table').prop('body')[0][2]).toBe('12.3%');
});

it('should process raw data', async () => {
  await shallow(
    <Table
      {...props}
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      formatter={v => v}
      configuration={{}}
    />
  );

  expect(processRawData).toHaveBeenCalled();
});

it('should display an error message for a non-object result (single number)', async () => {
  const node = await shallow(
    <Table {...props} data={7} errorMessage="Error" formatter={v => v} configuration={{}} />
  );

  expect(node.find('ReportBlankSlate')).toBePresent();
  expect(node.find('ReportBlankSlate').prop('message')).toBe('Error');
});

it('should display an error message if no data is provided', async () => {
  const node = await shallow(
    <Table {...props} errorMessage="Error" formatter={v => v} configuration={{}} />
  );

  expect(node.find('ReportBlankSlate')).toBePresent();
  expect(node.find('ReportBlankSlate').prop('message')).toBe('Error');
});

it('should display an error message if data is null', async () => {
  const node = await shallow(
    <Table {...props} data={null} errorMessage="Error" formatter={v => v} configuration={{}} />
  );

  expect(node.find('ReportBlankSlate')).toBePresent();
  expect(node.find('ReportBlankSlate').prop('message')).toBe('Error');
});

it('should not display an error message if data is valid', async () => {
  const node = await shallow(
    <Table
      {...props}
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      errorMessage="Error"
      formatter={v => v}
      configuration={{}}
    />
  );

  expect(node.find('ReportBlankSlate')).not.toBePresent();
});

it('should format data according to the provided formatter', async () => {
  const node = await shallow(
    <Table
      {...props}
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => 2 * v}
      configuration={{}}
    />
  );

  expect(node.find('Table').prop('body')).toEqual([['a', 2], ['b', 4], ['c', 6]]);
});

it('should return correct labels and body when combining to table report', async () => {
  const dataProps = {
    data: [
      {
        a: 1,
        b: 2,
        c: 3
      },
      {
        a: 1,
        b: 2,
        c: 3
      }
    ],
    labels: [['key', 'value'], ['key', 'value']],
    reportType: 'combined',
    property: 'frequency',
    processInstanceCount: [100, 100],
    reportsNames: ['Report A', 'Report B'],
    configuration: {}
  };

  const node = await shallow(<Table {...props} {...dataProps} />);
  expect(node.instance().processCombinedData()).toEqual({
    head: [
      'key',
      {label: 'Report A', columns: ['value', 'Relative Frequency']},
      {label: 'Report B', columns: ['value', 'Relative Frequency']}
    ],
    body: [
      ['a', 1, '12.3%', 1, '12.3%'],
      ['b', 2, '12.3%', 2, '12.3%'],
      ['c', 3, '12.3%', 3, '12.3%']
    ]
  });
});

it('should not include a column if it is hidden in the configuration', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const node = shallow(
    <Table
      {...props}
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      configuration={{hideAbsoluteValue: true}}
      property="frequency"
    />
  );

  expect(node.find('Table').prop('body')).toEqual([['a', '12.3%'], ['b', '12.3%'], ['c', '12.3%']]);
});
