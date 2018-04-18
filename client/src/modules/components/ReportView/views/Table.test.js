import React from 'react';
import {mount} from 'enzyme';

import Table from './Table';

jest.mock('components', () => {
  return {
    Table: ({head, body}) => <div>{JSON.stringify({head, body})}</div>
  };
});

it('should display data for key-value pairs', () => {
  const node = mount(
    <Table
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => v}
    />
  );

  expect(node).toIncludeText('a');
  expect(node).toIncludeText('b');
  expect(node).toIncludeText('c');
  expect(node).toIncludeText('1');
  expect(node).toIncludeText('2');
  expect(node).toIncludeText('3');
});

it('should display data for a list of objects', () => {
  const node = mount(
    <Table
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      formatter={v => v}
    />
  );

  expect(node).toIncludeText('foo');
  expect(node).toIncludeText('bar');
  expect(node).toIncludeText('asdf');
  expect(node).toIncludeText('ghjk');
});

it('should display variables for a list of objects with variables', () => {
  const node = mount(
    <Table
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      formatter={v => v}
    />
  );

  expect(node).toIncludeText('bla');
  expect(node).toIncludeText('ruvnvr');
});

it('should display error if no variables available', () => {
  const node = mount(
    <Table data={[{prop1: 'foo', prop2: 'bar', variables: {}}]} formatter={v => v} />
  );

  expect(node).toIncludeText('foo');
  expect(node).toIncludeText('bar');
  expect(node).not.toIncludeText('Error');
});

it('should display object properties for a list of objects', () => {
  const node = mount(
    <Table
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      formatter={v => v}
    />
  );

  expect(node).toIncludeText('prop1');
  expect(node).toIncludeText('prop2');
});

it('should display an error message for a non-object result (single number)', () => {
  const node = mount(<Table data={7} errorMessage="Error" formatter={v => v} />);

  expect(node).toIncludeText('Error');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Table errorMessage="Error" formatter={v => v} />);

  expect(node).toIncludeText('Error');
});

it('should display an error message if data is null', () => {
  const node = mount(<Table data={null} errorMessage="Error" formatter={v => v} />);

  expect(node).toIncludeText('Error');
});

it('should not display an error message if data is valid', () => {
  const node = mount(
    <Table
      data={[
        {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
        {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
      ]}
      errorMessage="Error"
      formatter={v => v}
    />
  );

  expect(node).not.toIncludeText('Error');
});

it('should format data according to the provided formatter', () => {
  const node = mount(
    <Table
      data={{
        a: 1,
        b: 2,
        c: 3
      }}
      formatter={v => 2 * v}
    />
  );

  expect(node).toIncludeText('2');
  expect(node).toIncludeText('4');
  expect(node).toIncludeText('6');
});

it('should exclude hidden columns', () => {
  const node = mount(
    <Table
      data={[
        {
          a: 1,
          b: 2,
          c: 3,
          variables: {}
        }
      ]}
      hiddenColumns={['b']}
    />
  );

  expect(node).toIncludeText('"a"');
  expect(node).not.toIncludeText('"b"');
  expect(node).toIncludeText('"c"');
});
