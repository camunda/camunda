/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {Dropdown} from 'components';

import EntityList from './EntityList';

const props = {
  name: 'EntityList Name',
  empty: 'Empty Message',
  children: <div>Some additional Content</div>,
  action: () => <button>Click Me</button>,
  data: [
    {
      id: 'aCollectionId',
      name: 'aCollectionName',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Collection',
      actions: [{icon: 'edit', text: 'Edit', action: jest.fn()}],
    },
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Dashboard',
    },
    {
      id: 'aReportId',
      name: 'aReport',
      meta: ['Some info', 'Some additional info', 'Some other info', 'special info'],
      icon: 'iconType',
      type: 'Report',
      link: 'link/to/somewhere',
    },
  ],
};

it('should match snapshot', () => {
  const node = shallow(<EntityList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show a loading indicator', () => {
  const node = shallow(<EntityList isLoading />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show an empty message if no entities exist', () => {
  const node = shallow(<EntityList {...props} data={[]} />);

  expect(node.find('.empty')).toExist();
});

it('should filter results based on search input', () => {
  const node = shallow(<EntityList {...props} />);
  node.find('SearchField').simulate('change', 'adashboard');
  expect(node.find('ListItem').length).toBe(1);

  node.find('SearchField').simulate('change', 'special info');
  expect(node.find('ListItem').length).toBe(1);
});

it('should show no result found text when no matching entities were found', () => {
  const node = shallow(<EntityList {...props} />);

  node.find('SearchField').simulate('change', 'not found entity');

  expect(node.find('.empty')).toIncludeText('No results found');
});

it('should pass a hasWarning prop to all ListEntries if one of them has a warning', () => {
  const warningData = update(props.data, {0: {$merge: {warning: 'some warning'}}});

  const node = shallow(<EntityList {...props} data={warningData} />);

  node.find('ListItem').forEach((node) => expect(node.prop('hasWarning')).toBe(true));
});

it('should show a column header if specified', () => {
  const node = shallow(<EntityList {...props} columns={['Name', 'Meta 1', 'Meta 2', 'Meta 3']} />);

  expect(node.find('.columnHeaders')).toMatchSnapshot();
});

it('should hide search for embedded entitylist', () => {
  const node = shallow(<EntityList {...props} embedded />);

  expect(node.find('SearchField')).not.toExist();
});

it('should have a sorting menu if at least one column is sortable', () => {
  const node = shallow(
    <EntityList {...props} columns={[{name: 'Name', key: 'name', defaultOrder: 'asc'}, 'Meta 1']} />
  );

  expect(node.find('.sortMenu')).toExist();
});

it('should always call onChange with default order from the sorting menu', () => {
  const spy = jest.fn();
  const node = shallow(
    <EntityList
      {...props}
      columns={[{name: 'Name', key: 'name', defaultOrder: 'asc'}, 'Meta 1']}
      sorting={{key: 'name', order: 'asc'}}
      onChange={spy}
    />
  );

  node.find('.sortMenu').find(Dropdown.Option).simulate('click');

  expect(spy).toHaveBeenCalledWith('name', 'asc');
});

it('should not show a header entry for column entries that are hidden', () => {
  const node = shallow(
    <EntityList
      {...props}
      columns={['Name', {name: 'hidden column', hidden: true}, 'Another Column']}
    />
  );

  expect(node.find('.columnHeaders')).toMatchSnapshot();
});

it('should indicate which columns are sortable', () => {
  const node = shallow(
    <EntityList
      {...props}
      columns={['Name', {name: 'sortable', key: 'sortKey', defaultOrder: 'asc'}, 'Another Column']}
    />
  );

  expect(node.find('.columnHeaders div').at(1)).toHaveClassName('sortable');
  expect(node.find('.columnHeaders div').at(0)).not.toHaveClassName('sortable');
});

it('should indicate which column is sorted', () => {
  const node = shallow(
    <EntityList
      {...props}
      columns={[
        'Name',
        {name: 'sortable', key: 'sortKey', defaultOrder: 'asc'},
        {name: 'Another Column', key: 'sortKey2', defaultOrder: 'desc'},
      ]}
      sorting={{key: 'sortKey2', order: 'asc'}}
    />
  );

  expect(node.find('.columnHeaders div').at(1)).not.toHaveClassName('sorted');
  expect(node.find('.columnHeaders div').at(2)).toHaveClassName('sorted');
});

it('should call onChange when clicking on the header column', () => {
  const spy = jest.fn();
  const node = shallow(
    <EntityList
      {...props}
      columns={[{name: 'sortable', key: 'sortKey', defaultOrder: 'asc'}]}
      onChange={spy}
    />
  );

  node.find('.columnHeaders span').simulate('click');

  expect(spy).toHaveBeenCalledWith('sortKey', 'asc');
});

it('should reverse the order when clicking on a header column that is already sorted', () => {
  const spy = jest.fn();
  const node = shallow(
    <EntityList
      {...props}
      columns={[{name: 'sortable', key: 'sortKey', defaultOrder: 'asc'}]}
      sorting={{key: 'sortKey', order: 'asc'}}
      onChange={spy}
    />
  );

  node.find('.columnHeaders span').simulate('click');

  expect(spy).toHaveBeenCalledWith('sortKey', 'desc');
});

it('should select and deselect a list item', () => {
  const node = shallow(<EntityList {...props} />);

  node
    .find('ListItem')
    .at(0)
    .simulate('selectionChange', {target: {checked: true}});

  expect(node.find({isSelected: true})).toExist();

  node
    .find('ListItem')
    .at(0)
    .simulate('selectionChange', {target: {checked: false}});

  expect(node.find({isSelected: true})).not.toExist();
});

it('should select/deselect all selectable events in view', () => {
  const customProps = {
    ...props,
    data: [
      props.data[0],
      {...props.data[1], actions: [{icon: 'delete', text: 'Delete'}]},
      props.data[2],
    ],
  };

  const node = shallow(<EntityList {...customProps} columns={['Name']} />);

  node
    .find('.columnHeaders')
    .find({type: 'checkbox'})
    .simulate('change', {target: {checked: true}});

  expect(node.find({isSelected: true}).length).toBe(2);

  node
    .find('.columnHeaders')
    .find({type: 'checkbox'})
    .simulate('change', {target: {checked: false}});

  expect(node.find({isSelected: true})).not.toExist();

  node
    .find('ListItem')
    .at(0)
    .simulate('selectionChange', {target: {checked: true}});

  node.find('SearchField').simulate('change', 'adashboard');

  expect(node.find('.columnHeaders').find({type: 'checkbox'}).prop('checked')).toBe(false);

  node
    .find('.columnHeaders')
    .find({type: 'checkbox'})
    .simulate('change', {target: {checked: true}});

  node.find('SearchField').simulate('change', '');

  expect(node.find({isSelected: true}).length).toBe(2);

  node.find('SearchField').simulate('change', 'aCollectionName');

  node
    .find('.columnHeaders')
    .find({type: 'checkbox'})
    .simulate('change', {target: {checked: false}});

  node.find('SearchField').simulate('change', '');

  expect(node.find({isSelected: true}).length).toBe(1);
});

it('should should reset the selection on data change', () => {
  const node = shallow(
    <EntityList
      {...props}
      columns={[
        'Name',
        {name: 'sortable', key: 'sortKey', defaultOrder: 'asc'},
        {name: 'Another Column', key: 'sortKey2', defaultOrder: 'desc'},
      ]}
    />
  );

  node
    .find('.columnHeaders')
    .find({type: 'checkbox'})
    .simulate('change', {target: {checked: true}});

  runLastEffect();

  expect(node.find({isSelected: true})).not.toExist();
});

it('should show bulk operation dropdown', () => {
  const node = shallow(<EntityList {...props} bulkActions={<div className="option" />} />);

  expect(node.find('.bulkMenu Dropdown')).not.toExist();

  node
    .find('ListItem')
    .at(0)
    .simulate('selectionChange', {target: {checked: true}});

  expect(node.find('.bulkMenu Dropdown')).toExist();
});

it('should pass selected entries to dropdown options', () => {
  const node = shallow(<EntityList {...props} bulkActions={<div className="option" />} />);

  node
    .find('ListItem')
    .at(0)
    .simulate('selectionChange', {target: {checked: true}});

  expect(node.find('.bulkMenu .option').prop('selectedEntries')).toEqual([props.data[0]]);
});

it('should pass the selection state to action', () => {
  const spy = jest.fn();
  const node = shallow(<EntityList {...props} action={spy} />);

  expect(spy).toHaveBeenCalledWith(false);
  spy.mockClear();

  node
    .find('ListItem')
    .at(0)
    .simulate('selectionChange', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith(true);
});

it('should display extra text in the header if specified', () => {
  const node = shallow(<EntityList {...props} headerText="foo" />);

  expect(node.find('.header .headerText')).toIncludeText('foo');
});
