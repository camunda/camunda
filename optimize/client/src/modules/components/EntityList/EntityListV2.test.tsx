/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {ComponentProps} from 'react';
import {DataTable} from '@camunda/design-system';

import EntityListV2 from './EntityListV2';

type Row = ComponentProps<typeof EntityListV2>['rows'][number];
type SortState = {id: string; desc: boolean}[];

const props: ComponentProps<typeof EntityListV2> = {
  title: 'EntityList Name',
  rows: [
    {
      id: 'aCollectionId',
      name: 'aCollectionName',
      link: 'link/to/somewhere',
      meta: ['Some info', 'Some additional info'],
      icon: 'iconType',
      type: 'Collection',
      actions: [{icon: 'edit', text: 'Edit', action: jest.fn()}],
    },
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      link: 'link/to/somewhere',
      meta: ['Some info', 'Some additional info'],
      icon: 'iconType',
      type: 'Dashboard',
      actions: [],
    },
  ],
  headers: ['Name', 'Meta 1', 'Meta 2'],
  action: <div className="action" />,
  onChange: jest.fn(),
};

function getTable(node: ReturnType<typeof shallow>) {
  return node.find(DataTable);
}

// The table's props are unions -- sorting and selection can each be `false` -- so the assertions
// below narrow them to the shape this component always passes.
function getSorting(node: ReturnType<typeof shallow>) {
  return getTable(node).prop('sorting') as {
    sortState: SortState;
    onSortingChange: (state: SortState) => void;
  };
}

function getSelection(node: ReturnType<typeof shallow>) {
  return getTable(node).prop('rowSelection') as {
    selectedRowIds: Record<string, boolean>;
    onSelectedRowsChange: (selection: Record<string, boolean>) => void;
  };
}

function getRowActions(node: ReturnType<typeof shallow>) {
  return getTable(node).prop('rowActions') as {
    visible: (row: Row) => boolean;
    onClick: (row: Row) => void;
  }[];
}

it('should show nothing if headers are empty', () => {
  const node = shallow(<EntityListV2 {...props} headers={[]} />);

  expect(node).toBeEmptyRender();
});

it('should show the provided empty state if there are no rows', () => {
  const node = shallow(
    <EntityListV2 {...props} rows={[]} emptyStateComponent={<div className="emptyState" />} />
  );

  expect(node.find('.emptyState')).toExist();
});

it('should show a loading indicator instead of the empty state while loading', () => {
  const node = shallow(
    <EntityListV2
      {...props}
      rows={[]}
      isLoading
      emptyStateComponent={<div className="emptyState" />}
    />
  );

  expect(node.find('Loading')).toExist();
  expect(node.find('.emptyState')).not.toExist();
});

it('should build a column per header', () => {
  const node = shallow(<EntityListV2 {...props} />);

  expect(getTable(node).prop('columns')).toHaveLength(3);
});

it('should only allow sorting on object headers once sorting is in use', () => {
  const headers = [{name: 'Name', key: 'name'}, 'Meta 1'];
  const node = shallow(<EntityListV2 {...props} headers={headers} />);

  expect(getTable(node).prop('columns')[0]!.enableSorting).toBe(false);

  node.setProps({sorting: {key: 'name', order: 'asc'}});

  expect(getTable(node).prop('columns')[0]!.enableSorting).toBe(true);
  expect(getTable(node).prop('columns')[1]!.enableSorting).toBe(false);
});

it('should pass the current sorting to the table', () => {
  const node = shallow(
    <EntityListV2
      {...props}
      headers={[{name: 'Name', key: 'name'}]}
      sorting={{key: 'name', order: 'desc'}}
    />
  );

  expect(getSorting(node).sortState).toEqual([{id: 'name', desc: true}]);
});

it('should call onChange when the table reports a new sorting', () => {
  const spy = jest.fn();
  const node = shallow(<EntityListV2 {...props} onChange={spy} />);

  getSorting(node).onSortingChange([{id: 'name', desc: true}]);

  expect(spy).toHaveBeenCalledWith('name', 'desc');
});

it('should call onChange without a sorting once the column is cleared', () => {
  const spy = jest.fn();
  const node = shallow(<EntityListV2 {...props} onChange={spy} />);

  getSorting(node).onSortingChange([]);

  expect(spy).toHaveBeenCalledWith(undefined, undefined);
});

it('should filter rows by name, type and string meta', () => {
  const node = shallow(<EntityListV2 {...props} />);
  const search = () => node.find('.entitySearch');

  search().simulate('change', {target: {value: 'aDashboard'}});
  expect(getTable(node).prop('data')).toHaveLength(1);

  search().simulate('change', {target: {value: 'Collection'}});
  expect(getTable(node).prop('data')).toHaveLength(1);

  search().simulate('change', {target: {value: 'Some info'}});
  expect(getTable(node).prop('data')).toHaveLength(2);

  search().simulate('change', {target: {value: 'no such entity'}});
  expect(getTable(node).prop('data')).toHaveLength(0);
});

it('should declare a row action slot for the row with the most actions', () => {
  const node = shallow(<EntityListV2 {...props} />);
  const rowActions = getRowActions(node);

  expect(rowActions).toHaveLength(1);
  expect(rowActions[0]!.visible(props.rows[0]!)).toBe(true);
  expect(rowActions[0]!.visible(props.rows[1]!)).toBe(false);
});

it('should invoke the action belonging to the row it was triggered on', () => {
  const node = shallow(<EntityListV2 {...props} />);

  getRowActions(node)[0]!.onClick(props.rows[0]!);

  expect(props.rows[0]!.actions[0]!.action).toHaveBeenCalled();
});

it('should only enable row selection when bulk actions are provided', () => {
  const node = shallow(<EntityListV2 {...props} />);

  expect(getTable(node).prop('rowSelection')).toBe(false);

  node.setProps({bulkActions: <div className="bulkAction" />});

  expect(getTable(node).prop('rowSelection')).toMatchObject({selectedRowIds: {}});
});

it('should replace the action with the bulk actions once rows are selected', () => {
  const node = shallow(<EntityListV2 {...props} bulkActions={<div className="bulkAction" />} />);

  expect(node.find('.action')).toExist();
  expect(node.find('.bulkAction')).not.toExist();

  getSelection(node).onSelectedRowsChange({aCollectionId: true});

  expect(node.find('.bulkAction')).toExist();
  expect(node.find('.action')).not.toExist();
});

it('should pass the selected rows to the bulk actions', () => {
  const node = shallow(<EntityListV2 {...props} bulkActions={<div className="bulkAction" />} />);

  getSelection(node).onSelectedRowsChange({aCollectionId: true});

  expect(node.find('.bulkAction').prop('selectedEntries')).toEqual([props.rows[0]]);
});

it('should render the description with the query and the filtered row count', () => {
  const node = shallow(
    <EntityListV2 {...props} description={(query, count) => `${query} ${count}`} />
  );

  expect(getTable(node).prop('description')).toBe('undefined 2');

  node.find('.entitySearch').simulate('change', {target: {value: 'aDashboard'}});

  expect(getTable(node).prop('description')).toBe('aDashboard 1');
});
