/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow, mount} from 'enzyme';
import {act} from 'react-dom/test-utils';

import {Icon, Select} from 'components';

import Table from './Table';

function generateData(amount: number) {
  const arr = [];
  for (let i = 0; i < amount; i++) {
    arr.push(['' + i]);
  }
  return arr;
}

it('shoud correctly format header', () => {
  const result = Table.formatColumns(['x', 'y', 'z']);

  expect(result).toMatchSnapshot();
});

it('should correctly format multi-level header', () => {
  const result = Table.formatColumns(['x', {label: 'a', columns: ['i', 'j']}]);

  expect(result).toMatchSnapshot();
});

it('should support explicit id and titles for columns', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X', title: 'X'},
    'Y',
    {id: 'column3', label: 'Z', title: 'Z'},
  ]);

  expect(result).toMatchSnapshot();
});

it('should be possible to explicitly disable sorting on a column', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X'},
    {id: 'column3', label: 'Z', sortable: false},
  ]);

  expect(result[0]?.disableSortBy).toBe(false);
  expect(result[1]?.disableSortBy).toBe(true);
});

it('should support explicit id for nested columns', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X', columns: ['i', {id: 'columnJ', label: 'j'}]},
    'Y',
    {label: 'Z', columns: [{id: 'columnZ1', label: 'Z1'}, 'Z2']},
  ]);

  expect(result).toMatchSnapshot();
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
  const node = mount(<Table {...{head: ['a'], body: generateData(21), foot: []}} />);

  expect(node.find('.cds--pagination')).toExist();
});

it('should not show pagination if data contains more than 20 rows, but disablePagination flag is set', () => {
  const node = mount(
    <Table {...{head: ['a'], body: generateData(21), foot: []}} disablePagination />
  );

  expect(node.find('.cds--pagination')).not.toExist();
});

it('should not show pagination if data contains less than or equal to 20 rows', () => {
  const node = shallow(<Table {...{head: ['a'], body: generateData(20), foot: []}} />);

  expect(node.find('.cds--pagination')).not.toExist();
});

it('should call the updateSorting method on click on header', () => {
  const spy = jest.fn();
  const node = mount(
    <Table {...{head: ['a'], body: generateData(20), foot: []}} updateSorting={spy} />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});

  expect(spy).toHaveBeenCalledWith('a', 'asc');
});

it('should call the updateSorting method to sort by key/value if result is map', () => {
  const spy = jest.fn();
  const node = mount(
    <Table
      {...{head: ['a'], body: generateData(20), foot: [], resultType: 'map'}}
      updateSorting={spy}
    />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});

  expect(spy).toHaveBeenCalledWith('key', 'asc');
});

it('should call the updateSorting method to sort by Label if sortByLabel is true', () => {
  const spy = jest.fn();
  const node = mount(
    <Table
      {...{head: ['a'], body: generateData(20), foot: [], sortByLabel: true, resultType: 'map'}}
      updateSorting={spy}
    />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});

  expect(spy).toHaveBeenCalledWith('label', 'asc');
});

it('should show empty message', () => {
  const node = shallow(<Table head={['a']} body={[]} />);

  expect(node.find('.noData')).toExist();
});

it('should show empty message if all columns are hidden', () => {
  const node = shallow(<Table head={[]} body={[]} totalEntries={100} />);

  expect(node.find('.noData')).toExist();
});

it('should add a noOverflow classname to tds with Selects', () => {
  const node = mount(<Table head={['a']} body={[[<Select />]]} />);

  expect(node.find('td')).toHaveClassName('noOverflow');
});

it('should show a loading state when specified', () => {
  const node = mount(<Table head={['a']} body={[]} loading={true} />);

  expect(node.find('.loading')).toExist();
  expect(node.find('LoadingIndicator')).toExist();
});

it('should use manual pagination values if specified', () => {
  const node = mount(<Table head={['a']} body={[]} totalEntries={250} defaultPageSize={100} />);

  expect(node.find('.cds--pagination')).toIncludeText('page 1 of 3');
});

it('should invoke fetchData when the page is change', async () => {
  const spy = jest.fn();
  const node = mount(
    <Table head={['a']} body={[]} fetchData={spy} totalEntries={250} defaultPageSize={100} />
  );

  await act(async () => {
    await runAllEffects();
    node.find('button.cds--pagination__button--forward').simulate('click');
    runAllEffects();

    expect(spy).toHaveBeenCalledWith({pageIndex: 1, pageSize: 100});
  });
});

it('should go to the last page if data changes in a way that current page is empty', async () => {
  const spy = jest.fn();
  const node = mount(<Table head={['a']} body={[]} fetchData={spy} totalEntries={100} />);

  await act(async () => {
    await runAllEffects();

    while (!node.find('button.cds--pagination__button--forward').prop('disabled')) {
      node.find('button.cds--pagination__button--forward').simulate('click');
      runAllEffects();
    }

    spy.mockClear();
    node.setProps({totalEntries: 50});
    runAllEffects();

    expect(spy).toHaveBeenCalledWith({pageIndex: 4, pageSize: 20});
  });
});

it('should be sorted desc by default when allowed to sort locally', () => {
  const node = mount(
    <Table {...{head: ['a'], body: generateData(21), foot: []}} allowLocalSorting />
  );

  expect(node.find(Icon).at(0).prop('type')).toBe('up');
  expect(node.find('td').at(0).childAt(0).prop('value')).toBe('0');
});

it('should change sorting to asc when clicked on header', () => {
  const node = mount(
    <Table {...{head: ['a'], body: generateData(21), foot: []}} allowLocalSorting />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});
  expect(node.find(Icon).at(0).prop('type')).toBe('down');
  expect(node.find('td').at(0).childAt(0).prop('value')).toBe('20');
});
