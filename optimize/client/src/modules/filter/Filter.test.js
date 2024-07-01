/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {NodeDateFilter} from './modals';
import Filter from './Filter';
import {filterSameTypeExistingFilters} from './service';

const props = {
  filterLevel: 'instance',
  data: [],
};

jest.mock('./service', () => ({filterSameTypeExistingFilters: jest.fn()}));

it('should contain a list of filters', () => {
  const node = shallow(<Filter {...props} />);

  expect(node.find('FilterList')).toExist();
});

it('should contain a List of Filter options', () => {
  const node = shallow(<Filter {...props} />);

  expect(node.find('InstanceFilters')).toExist();
});

it('should not contain any filter modal when no newFilter is selected', () => {
  const node = shallow(<Filter {...props} />);

  expect(node).not.toIncludeText('DateFilter');
  expect(node).not.toIncludeText('VariableFilter');
  expect(node).not.toIncludeText('NodeFilter');
});

it('should contain a filter modal when a newFilter should be created', () => {
  const node = shallow(<Filter {...props} />);

  node.instance().openNewFilterModal('instance')('instanceStartDate')();

  expect(node).toIncludeText('DateFilter');
});

it('should contain an edit filter modal when a filter should be edited', () => {
  const node = shallow(<Filter {...props} data={[{type: 'instanceStartDate'}]} />);

  node.instance().openEditFilterModal({
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'instanceStartDate',
  })();

  expect(node).toIncludeText('DateFilter');
});

it('should contain a FilterModal component based on the selected new Filter', () => {
  const node = shallow(<Filter {...props} />);

  node.instance().openNewFilterModal('instance')('multipleVariable')();

  expect(node).toIncludeText('MultipleVariableFilter');
  expect(node).not.toIncludeText('DateFilter');
});

it('should contain a EditFilterModal component based on the Filter selected for edition', () => {
  const node = shallow(<Filter {...props} data={[{type: 'variable'}]} />);

  node.instance().openEditFilterModal({
    data: {
      data: [{operator: 'bar', type: 'baz', value: 'foo'}],
    },
    type: 'multipleVariable',
  })();
  expect(node).toIncludeText('MultipleVariableFilter');
  expect(node).not.toIncludeText('DateFilter');
});

it('should add a filter to the list of filters', () => {
  filterSameTypeExistingFilters.mockImplementationOnce((filters) => filters);
  const spy = jest.fn();
  const sampleFilter = {
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'qux',
  };
  const previousFilters = [sampleFilter];

  const node = shallow(
    <Filter {...props} data={previousFilters} onChange={spy} filterLevel="view" />
  );

  node.instance().addFilter({type: 'Filter 2', filterLevel: 'view'});

  expect(spy.mock.calls[0][0].filter).toEqual({
    $set: [sampleFilter, {type: 'Filter 2', filterLevel: 'view'}],
  });
});

it('should edit the edited filter', () => {
  const spy = jest.fn();
  const sampleFilter = {
    data: null,
    type: 'qux',
    filterLevel: 'instance',
  };

  const filters = [sampleFilter, {data: null, type: 'foo', filterLevel: 'instance'}];
  const node = shallow(<Filter {...props} data={filters} onChange={spy} />);

  node.instance().setState({
    editFilter: sampleFilter,
  });

  node.instance().editFilter({data: null, type: 'bar'});

  expect(spy.mock.calls[0][0].filter).toEqual({
    0: {$set: {data: null, type: 'bar', filterLevel: 'instance'}},
  });
});

it('should remove a filter from the list of filters', () => {
  const spy = jest.fn();
  const previousFilters = ['Filter 1', 'Filter 2', 'Filter 3'];

  const node = shallow(<Filter {...props} data={previousFilters} onChange={spy} />);

  node.instance().deleteFilter('Filter 2');

  expect(spy.mock.calls[0][0].filter).toEqual({$set: ['Filter 1', 'Filter 3']});
});

it('should pass the information about a missing process definition to the component rendering the dropdown', () => {
  const node = shallow(<Filter {...props} />);

  expect(node.find('InstanceFilters').prop('processDefinitionIsNotSelected')).toBe(true);
});

it('should invoke filterSameTypeExistingFilters to remove any previous filters of the same type', () => {
  filterSameTypeExistingFilters.mockReturnValueOnce([]);
  const previousFilters = [{type: 'instanceStartDate', filterLevel: 'instance'}];
  const newFilter = {type: 'instanceStartDate', filterLevel: 'instance', value: 'new date'};
  const spy = jest.fn();

  const node = shallow(<Filter {...props} data={previousFilters} onChange={spy} />);

  node.instance().addFilter(newFilter);

  expect(filterSameTypeExistingFilters).toHaveBeenCalledWith(previousFilters, newFilter);

  expect(spy.mock.calls[0][0].filter).toEqual({$set: [newFilter]});
});

it('should render available filters depending on the provided filter level', () => {
  const node = shallow(<Filter {...props} filterLevel="view" />);

  expect(node.find('InstanceFilters')).not.toExist();
  expect(node.find('ViewFilters')).toExist();
});

it('should render two "Add Filter" dropdowns and all added filters if no filterLevel is set', () => {
  const filters = [
    {type: 'runningInstancesOnly', data: null, filterLevel: 'instance'},
    {type: 'runningFlowNodesOnly', data: null, filterLevel: 'view'},
  ];
  const node = shallow(<Filter data={filters} />);

  expect(node.find('InstanceFilters')).toExist();
  expect(node.find('ViewFilters')).toExist();
  expect(node.find('FilterList')).toExist();
  expect(node.find('FilterList').prop('data')).toEqual(filters);
});

it('should pass correct filter level to modal', () => {
  const node = shallow(<Filter {...props} />);

  node.find('InstanceFilters').prop('openNewFilterModal')('flowNodeStartDate')();

  expect(node.find(NodeDateFilter).prop('filterLevel')).toBe('instance');
  node.find(NodeDateFilter).prop('close')();

  node.find('FilterList').prop('openEditFilterModal')({
    type: 'flowNodeEndDate',
    filterLevel: 'view',
  })();

  expect(node.find(NodeDateFilter).prop('filterLevel')).toBe('view');
});
