import React from 'react';
import {mount, shallow} from 'enzyme';

import Table from './Table';
import {processRawData} from 'services';

import {getCamundaEndpoints, getRelativeValue} from './service';

jest.mock('components', () => {
  return {
    Table: ({head, body}) => <div>{JSON.stringify({head, body})}</div>,
    LoadingIndicator: props => (
      <div className="sk-circle" {...props}>
        Loading...
      </div>
    )
  };
});

jest.mock('services', () => {
  return {
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

it('should get the camunda endpoints for raw data', () => {
  getCamundaEndpoints.mockClear();
  mount(<Table configuration={{}} data={[{}]} />);

  expect(getCamundaEndpoints).toHaveBeenCalled();
});

it('should not get the camunda endpoints for non-raw-data tables', () => {
  getCamundaEndpoints.mockClear();
  mount(<Table configuration={{}} data={{}} />);

  expect(getCamundaEndpoints).not.toHaveBeenCalled();
});

it('should display data for key-value pairs', async () => {
  const node = await mount(
    <Table
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => v}
      configuration={{}}
    />
  );

  expect(node).toIncludeText('a');
  expect(node).toIncludeText('b');
  expect(node).toIncludeText('c');
  expect(node).toIncludeText('1');
  expect(node).toIncludeText('2');
  expect(node).toIncludeText('3');
});

it('should display the relative percentage for frequency views', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const node = mount(
    <Table
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

  expect(node).toIncludeText('12.3%');
});

it('should process raw data', async () => {
  await mount(
    <Table
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
  const node = await mount(
    <Table data={7} errorMessage="Error" formatter={v => v} configuration={{}} />
  );

  expect(node).toIncludeText('Error');
});

it('should display an error message if no data is provided', async () => {
  const node = await mount(<Table errorMessage="Error" formatter={v => v} configuration={{}} />);

  expect(node).toIncludeText('Error');
});

it('should display an error message if data is null', async () => {
  const node = await mount(
    <Table data={null} errorMessage="Error" formatter={v => v} configuration={{}} />
  );

  expect(node).toIncludeText('Error');
});

it('should not display an error message if data is valid', async () => {
  const node = await mount(
    <Table
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      errorMessage="Error"
      formatter={v => v}
      configuration={{}}
    />
  );

  expect(node).not.toIncludeText('Error');
});

it('should format data according to the provided formatter', async () => {
  const node = await mount(
    <Table
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => 2 * v}
      configuration={{}}
    />
  );

  expect(node).toIncludeText('2');
  expect(node).toIncludeText('4');
  expect(node).toIncludeText('6');
});

it('should return correct labels and body when combining to table report', async () => {
  const props = {
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

  const node = await mount(shallow(<Table {...props} />).get(0));
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
