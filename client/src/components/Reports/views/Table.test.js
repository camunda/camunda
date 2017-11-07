import React from 'react';
import {mount} from 'enzyme';

import Table from './Table';

jest.mock('components', () => {return {
  Table: ({data}) => <div>{JSON.stringify(data)}</div>
}});

it('should display data for key-value pairs', () => {
  const node = mount(<Table data={{
    a: 1,
    b: 2,
    c: 3
  }} />)

  expect(node).toIncludeText('a');
  expect(node).toIncludeText('b');
  expect(node).toIncludeText('c');
  expect(node).toIncludeText('1');
  expect(node).toIncludeText('2');
  expect(node).toIncludeText('3');
});

it('should display data for a list of objects', () => {
  const node = mount(<Table data={[
    {prop1: 'foo', prop2: 'bar'},
    {prop1: 'asdf', prop2: 'ghjk'}
  ]} />);

  expect(node).toIncludeText('foo');
  expect(node).toIncludeText('bar');
  expect(node).toIncludeText('asdf');
  expect(node).toIncludeText('ghjk');
});

it('should display object properties for a list of objects', () => {
  const node = mount(<Table data={[
    {prop1: 'foo', prop2: 'bar'},
    {prop1: 'asdf', prop2: 'ghjk'}
  ]} />);

  expect(node).toIncludeText('prop1');
  expect(node).toIncludeText('prop2');
});

it('should display an error message for a non-object result (single number)', () => {
  const node = mount(<Table data={7} />);

  expect(node).toIncludeText('Cannot display data');
});

it('should display an error message if no data is provided', () => {
  const node = mount(<Table />);

  expect(node).toIncludeText('Cannot display data');
});
