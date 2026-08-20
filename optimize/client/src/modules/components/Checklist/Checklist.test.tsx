/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactElement} from 'react';
import {runLastEffect} from '__mocks__/react';
import {TableRow} from '@carbon/react';
import {mount, shallow} from 'enzyme';

import Checklist from './Checklist';

const props = {
  selectedItems: [{id: 'item1'}],
  allItems: [{id: 'item1'}, {id: 'item2'}],
  onChange: jest.fn(),
  formatter: jest.fn().mockReturnValue([
    {id: 'item1', checked: true, label: 'item 1'},
    {id: 'item2', checked: false, label: 'item 2'},
    {id: 'id3', label: 'unauthorized', disabled: true},
  ]),
  onSearch: jest.fn().mockImplementation((value: string) =>
    [
      {id: 'item1', checked: true, label: 'item 1'},
      {id: 'item2', checked: false, label: 'item 2'},
      {id: 'id3', label: 'unauthorized', disabled: true},
    ].find((item) => item.id === value)
  ),
};

beforeEach(() => props.onChange.mockClear());

it('should display checklist properly', () => {
  const node = shallow(<Checklist {...props} />);

  const dataTable = node.find('Table').dive().find('DataTable').dive();
  const selectAll = dataTable.find('TableHeader').at(0).dive().find('TableSelectAll');
  const rows = dataTable.find(TableRow);

  expect(selectAll).toExist();
  expect(rows).toHaveLength(4);
});

it('should call the formatter with the list items data', () => {
  shallow(<Checklist {...props} />);

  expect(props.formatter).toHaveBeenCalledWith(props.allItems, props.selectedItems);
});

it('should invoke onSelect with the updated selected items', () => {
  const node = shallow(<Checklist {...props} />);

  const dataTable = node.find('Table').dive().find('DataTable').dive();
  dataTable
    .find(TableRow)
    .find({cell: {value: 'item 1'}})
    .parent()
    .simulate('click');

  expect(props.onChange).toHaveBeenCalledWith([]);

  dataTable
    .find(TableRow)
    .find({cell: {value: 'item 2'}})
    .parent()
    .simulate('click');
  expect(props.onChange).toHaveBeenCalledWith([{id: 'item1'}, {id: 'item2'}]);
});

it('should invoke onChange on selectAll/deselectAll', () => {
  const node = shallow(<Checklist {...props} />);

  const dataTable = node.find('Table').dive().find('DataTable').dive();
  const selectAll = dataTable.find('TableHeader').at(0).dive().find('TableSelectAll');

  selectAll.simulate('select', {target: {checked: true}});

  expect(props.onChange).toHaveBeenCalledWith(props.allItems);

  runLastEffect();

  selectAll.simulate('select', {target: {checked: false}});

  expect(props.onChange).toHaveBeenCalledWith([]);
});

it('should hide selectAll if there is only one item', () => {
  props.formatter.mockReturnValueOnce([{id: 'item1', checked: true, label: 'item 1'}]);
  const node = shallow(<Checklist {...props} />);
  const dataTable = node.find('Table').dive().find('DataTable').dive();
  const selectAll = dataTable.find('TableHeader').at(0).dive().find('TableSelectAll');

  expect(selectAll).not.toExist();
});

it('should filter items based on search', () => {
  const node = shallow(<Checklist {...props} />);
  const toolbar = shallow<ReactElement>(node.find('Table').prop('toolbar'));

  toolbar.find('TableToolbarSearch').simulate('change', {target: {value: 'item 1'}});

  const dataTable = node.find('Table').dive().find('DataTable').dive();

  expect(props.onSearch).toHaveBeenCalledWith('item 1');
  expect(dataTable.find(TableRow).find({cell: {value: 'item 1'}})).toExist();
  expect(dataTable.find(TableRow).find({cell: {value: 'item 2'}})).not.toExist();
});

it('should display the id if the label is null', () => {
  props.formatter.mockReturnValueOnce([
    {id: 'item1', checked: false, label: null, disabled: false},
  ]);

  const node = shallow(<Checklist {...props} />);

  const dataTable = node.find('Table').dive().find('DataTable').dive();

  expect(dataTable.find(TableRow).find({cell: {value: 'item1'}})).toExist();
});

it('should select all items in view', () => {
  props.formatter.mockReturnValueOnce([{id: 'item1', checked: true, label: 'item 1'}]);
  const node = shallow(<Checklist {...props} />);
  const toolbar = shallow<ReactElement>(node.find('Table').prop('toolbar'));

  toolbar.find('TableToolbarSearch').simulate('change', {target: {value: 'item'}});
  const dataTable = node.find('Table').dive().find('DataTable').dive();

  dataTable
    .find(TableRow)
    .find({cell: {value: 'Select all in view'}})
    .parent()
    .simulate('click');

  expect(props.onChange).toHaveBeenCalledWith([{id: 'item1'}, {id: 'item2'}]);
});

it('should hide toolbar if specified', () => {
  const node = shallow(<Checklist {...props} headerHidden />);

  expect(node.find('Table').prop<ReactElement>('toolbar')).toBe(false);
});

it('should prepend items to the checklist', () => {
  const node = mount(<Checklist {...props} preItems={['label']} />);
  const dataTable = node.find('Table').find('DataTable');

  expect(dataTable.find(TableRow).find({cell: {value: 'label'}})).toExist();
});

it('should allow overwriting the selectAll button with a custom header', () => {
  const node = shallow(<Checklist {...props} customHeader="Custom Header Content" />);
  const dataTable = node.find('Table').dive().find('DataTable').dive();
  const selectAll = dataTable.find('TableHeader').at(0).dive().find('TableSelectAll');
  const customHeader = dataTable.find('TableHeader').at(1).dive().find('span');

  expect(selectAll).not.toExist();
  expect(customHeader).toIncludeText('Custom Header Content');
});

it('should not show select all in view when hideSelectAllInView prop is passed', () => {
  const node = shallow(<Checklist {...props} />);
  const toolbar = shallow<ReactElement>(node.find('Table').prop('toolbar'));

  toolbar.find('TableToolbarSearch').simulate('change', {target: {value: 'item'}});
  let dataTable = node.find('Table').dive().find('DataTable').dive();

  expect(dataTable.find('.selectAllInView')).toExist();

  node.setProps({hideSelectAllInView: true});
  dataTable = node.find('Table').dive().find('DataTable').dive();

  expect(dataTable.find('.selectAllInView')).not.toExist();
});
