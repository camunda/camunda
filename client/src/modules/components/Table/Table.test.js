import React from 'react';
import {shallow} from 'enzyme';

import Table from './Table';
import ReactTable from 'react-table';

function generateData(amount) {
  const arr = [];
  for (let i = 0; i < amount; i++) {
    arr.push(['' + i]);
  }
  return arr;
}

it('shoud correctly format header', () => {
  const result = Table.formatColumns(['x', 'y', 'z']);

  expect(result).toEqual([
    {Header: 'x', accessor: 'x', minWidth: 100},
    {Header: 'y', accessor: 'y', minWidth: 100},
    {Header: 'z', accessor: 'z', minWidth: 100}
  ]);
});

it('should correctly format multi-level header', () => {
  const result = Table.formatColumns(['x', {label: 'a', columns: ['i', 'j']}]);

  expect(result).toEqual([
    {Header: 'x', accessor: 'x', minWidth: 100},
    {
      Header: 'a',
      columns: [
        {Header: 'i', accessor: 'ai', minWidth: 100},
        {Header: 'j', accessor: 'aj', minWidth: 100}
      ]
    }
  ]);
});

it('shoud correctly format body', () => {
  const result = Table.formatData(['Header 1', 'Header 2', 'Header 3'], [['a', 'b', 'c']]);

  expect(result).toEqual([{header1: 'a', header2: 'b', header3: 'c'}]);
});

it('should show pagination if data contains more than 20 rows', () => {
  const node = shallow(<Table {...{head: ['a'], body: generateData(21), foot: []}} />);

  expect(node.find(ReactTable)).toHaveProp('showPagination', true);
});

it('should not show pagination if data contains more than 20 rows, but disablePagination flag is set', () => {
  const node = shallow(
    <Table {...{head: ['a'], body: generateData(21), foot: []}} disablePagination />
  );

  expect(node.find(ReactTable)).toHaveProp('showPagination', false);
});

it('should not show pagination if data contains less than or equal to 20 rows', () => {
  const node = shallow(<Table {...{head: ['a'], body: generateData(20), foot: []}} />);

  expect(node.find(ReactTable)).toHaveProp('showPagination', false);
});

it('should make the table sortable if an updateSorting method is defined', () => {
  const node = shallow(
    <Table {...{head: ['a'], body: generateData(20), foot: []}} updateSorting={() => {}} />
  );

  expect(node.find(ReactTable)).toHaveProp('sortable', true);
});
