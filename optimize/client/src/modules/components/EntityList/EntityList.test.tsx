/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {ComponentProps} from 'react';
import {
  DataTable,
  TableBatchActions,
  TableContainer,
  TableHeader,
  TableToolbarContent,
} from '@carbon/react';

import EntityList from './EntityList';

const props: ComponentProps<typeof EntityList> = {
  title: 'EntityList Name',
  rows: [
    {
      id: 'aCollectionId',
      name: 'aCollectionName',
      link: 'link/to/somewhere',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Collection',
      actions: [{icon: 'edit', text: 'Edit', action: jest.fn()}],
    },
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      link: 'link/to/somewhere',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Dashboard',
      actions: [],
    },
    {
      id: 'aReportId',
      name: 'aReport',
      link: 'link/to/somewhere',
      meta: ['Some info', 'Some additional info', 'Some other info', 'special info'],
      icon: 'iconType',
      type: 'Report',
      actions: [],
    },
  ],
  headers: [],
  action: <div>Test Action</div>,
  onChange: jest.fn(),
};

it('should show a loading indicator', () => {
  const node = shallow(
    <EntityList {...props} headers={['Name', 'Meta 1', 'Meta 2', 'Meta 3', 'Meta 4']} isLoading />
  );

  expect(node.find('DataTableSkeleton')).toExist();
});

it('should show nothing if headers are empty', () => {
  const node = shallow(<EntityList {...props} headers={[]} />);

  expect(node).toBeEmptyRender();
});

it('should show an empty table if rows are empty', () => {
  const node = shallow(<EntityList {...props} headers={['Name', 'Meta 1']} rows={[]} />);

  expect(node.find('DataTable')).toExist();
});

it('should show provided empty state if rows are empty', () => {
  const node = shallow(
    <EntityList
      {...props}
      headers={['Name', 'Meta 1']}
      rows={[]}
      emptyStateComponent={<div className="emptyState" />}
    />
  );

  expect(node.find('.emptyState')).toExist();
});

it('should disable sorting if no sorting is applied', () => {
  const node = shallow(
    <EntityList {...props} headers={[{name: 'Name', key: 'name', defaultOrder: 'asc'}, 'Meta 1']} />
  );

  expect(node.find('DataTable').prop('isSortable')).toBe(false);
});

it('should disable sorting if there are no sortable object headers', () => {
  const node = shallow(
    <EntityList
      {...props}
      headers={[{name: 'Name', key: '', defaultOrder: 'asc'}, 'Meta 1']}
      sorting={{key: 'name', order: 'asc'}}
    />
  );

  expect(node.find(DataTable).prop('isSortable')).toBe(false);
});

it('should indicate which column is sorted', () => {
  const node = shallow(
    <EntityList
      {...props}
      headers={[
        'Name',
        {name: 'sortable', key: 'sortKey', defaultOrder: 'asc'},
        {name: 'Another Column', key: 'sortKey2', defaultOrder: 'desc'},
      ]}
      sorting={{key: 'sortKey2', order: 'asc'}}
    />
  );

  const dataTable = node.find(DataTable).dive();
  const secondColumnProps = dataTable.find(TableHeader).at(2).props();
  expect(secondColumnProps.isSortHeader).toBe(true);
  expect(secondColumnProps.sortDirection).toBe('ASC');
});

it('should call onChange when sorting by one of columns', () => {
  const spy = jest.fn();
  const node = shallow(
    <EntityList
      {...props}
      headers={[{name: 'sortable', key: 'sortKey', defaultOrder: 'asc'}]}
      onChange={spy}
    />
  );

  let dataTable = node.find(DataTable).dive();
  dataTable.find(TableHeader).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('sortKey', 'asc');

  node.setProps({sorting: {key: 'sortKey', order: 'asc'}});
  dataTable = node.find(DataTable).dive();
  dataTable.find(TableHeader).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('sortKey', 'desc');

  node.setProps({sorting: {key: 'sortKey', order: 'desc'}});
  dataTable = node.find(DataTable).dive();
  dataTable.find(TableHeader).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith(undefined, undefined);
});

it('should reverse sorting when default order is desc', () => {
  const spy = jest.fn();
  const node = shallow(
    <EntityList
      {...props}
      headers={[{name: 'sortable', key: 'sortKey', defaultOrder: 'desc'}]}
      onChange={spy}
    />
  );

  let dataTable = node.find(DataTable).dive();
  dataTable.find(TableHeader).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('sortKey', 'desc');

  node.setProps({sorting: {key: 'sortKey', order: 'desc'}});
  dataTable = node.find(DataTable).dive();
  dataTable.find(TableHeader).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith('sortKey', 'asc');

  node.setProps({sorting: {key: 'sortKey', order: 'asc'}});
  dataTable = node.find(DataTable).dive();
  dataTable.find(TableHeader).at(0).simulate('click');

  expect(spy).toHaveBeenCalledWith(undefined, undefined);
});

it('should show bulk operation options if bulkAction is specified', () => {
  const node = shallow(
    <EntityList {...props} headers={['header1']} bulkActions={<div className="option" />} />
  );

  const dataTable = node.find(DataTable).dive();
  expect(dataTable.find(TableBatchActions).find('.option')).toExist();
});

it('disable rows without actions', () => {
  const node = shallow(<EntityList {...props} headers={['Name']} />);

  const rows = node.find(DataTable).prop('rows');
  expect(rows[0].disabled).toBe(false);
  expect(rows[1].disabled).toBe(true);
});

it('should render description', () => {
  const node = shallow(
    <EntityList {...props} headers={['Name']} description={(query, count) => `${query} ${count}`} />
  );

  expect(node.find(DataTable).dive().find(TableContainer).prop('description')).toBe('undefined 3');

  node.setProps({description: 'description'});
  expect(node.find(DataTable).dive().find(TableContainer).prop('description')).toBe('description');
});

it('should render action and pass disabled and tabIndex props', () => {
  const node = shallow(<EntityList {...props} headers={['Name']} />);
  const dataTable = node.find(DataTable).dive();

  expect(dataTable.find(TableToolbarContent).childAt(1).props()).toMatchObject({
    disabled: false,
    tabIndex: 0,
  });
});
