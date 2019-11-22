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

it('should support explicit id for columns', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X'},
    'Y',
    {id: 'column3', label: 'Z'}
  ]);

  expect(result).toEqual([
    {Header: 'X', accessor: 'column1', minWidth: 100},
    {Header: 'Y', accessor: 'y', minWidth: 100},
    {Header: 'Z', accessor: 'column3', minWidth: 100}
  ]);
});

it('shoud correctly format body', () => {
  const result = Table.formatData(['Header 1', 'Header 2', 'Header 3'], [['a', 'b', 'c']]);

  expect(result).toEqual([{header1: 'a', header2: 'b', header3: 'c'}]);
});

it('should format structured body data', () => {
  const result = Table.formatData(
    ['Header 1', 'Header 2', 'Header 3'],
    [{content: ['a', 'b', 'c'], props: {foo: 'bar'}}]
  );

  expect(result).toEqual([{header1: 'a', header2: 'b', header3: 'c', __props: {foo: 'bar'}}]);
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
    .prop('getTheadThProps')(0, 0, {id: 'someId'})
    .onClick({target: 'header'});

  expect(spy).toHaveBeenCalledWith('someId', 'asc');
});

it('should call the updateSorting method to sort by key/value if result is map', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...{head: ['a'], body: generateData(20), foot: [], resultType: 'map'}}
      updateSorting={spy}
    />
  );

  node
    .instance()
    .applySortingBehavior({columns: [{accessor: 'test'}]}, null, {id: 'test'})
    .onClick({target: 'header'});

  expect(spy).toHaveBeenCalledWith('key', 'asc');
});

it('should call the updateSorting method to sort by Label if sortByLabel is true', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...{head: ['a'], body: generateData(20), foot: [], sortByLabel: true, resultType: 'map'}}
      updateSorting={spy}
    />
  );

  node
    .instance()
    .applySortingBehavior({columns: [{accessor: 'test'}]}, null, {id: 'test'})
    .onClick({target: 'header'});

  expect(spy).toHaveBeenCalledWith('label', 'asc');
});

it('should set empty classname', () => {
  const node = shallow(<Table head={['a']} body={[]} />);

  expect(node).toHaveClassName('empty');
});
