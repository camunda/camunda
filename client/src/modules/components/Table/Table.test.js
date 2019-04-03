/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
  const result = Table.formatColumns('raw', ['x', 'y', 'z']);

  expect(result).toEqual([
    {Header: 'x', accessor: 'x', minWidth: 100, sortBy: 'x'},
    {Header: 'y', accessor: 'y', minWidth: 100, sortBy: 'y'},
    {Header: 'z', accessor: 'z', minWidth: 100, sortBy: 'z'}
  ]);
});

it('should correctly format multi-level header', () => {
  const result = Table.formatColumns('raw', ['x', {label: 'a', columns: ['i', 'j']}]);

  expect(result).toEqual([
    {Header: 'x', accessor: 'x', minWidth: 100, sortBy: 'x'},
    {
      Header: 'a',
      columns: [
        {Header: 'i', accessor: 'ai', minWidth: 100, sortBy: 'ai'},
        {Header: 'j', accessor: 'aj', minWidth: 100, sortBy: 'aj'}
      ]
    }
  ]);
});

it('should support explicit id for columns', () => {
  const result = Table.formatColumns('raw', [
    {id: 'column1', label: 'X'},
    'Y',
    {id: 'column3', label: 'Z'}
  ]);

  expect(result).toEqual([
    {Header: 'X', accessor: 'column1', minWidth: 100, sortBy: 'column1'},
    {Header: 'Y', accessor: 'y', minWidth: 100, sortBy: 'y'},
    {Header: 'Z', accessor: 'column3', minWidth: 100, sortBy: 'column3'}
  ]);
});

it('should add sortBy:key/value props to head columns if resultType is a map', () => {
  const result = Table.formatColumns('frequencyMap', [
    {id: 'column1', label: 'X'},
    'Y',
    {id: 'column3', label: 'Z'}
  ]);

  expect(result).toEqual([
    {Header: 'X', accessor: 'column1', minWidth: 100, sortBy: 'key'},
    {Header: 'Y', accessor: 'y', minWidth: 100, sortBy: 'value'},
    {Header: 'Z', accessor: 'column3', minWidth: 100, sortBy: 'value'}
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

it('should call the updateSorting method on click on header', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table {...{head: ['a'], body: generateData(20), foot: []}} updateSorting={spy} />
  );

  node
    .find(ReactTable)
    .prop('getTheadThProps')(0, 0, {sortBy: 'someId'})
    .onClick({target: 'header'});

  expect(spy).toHaveBeenCalledWith('someId', 'asc');
});
