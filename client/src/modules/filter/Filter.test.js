/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Filter from './Filter';

import {shallow} from 'enzyme';

const props = {
  filterLevel: 'instance',
  data: [],
};

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

  node.instance().openNewFilterModal('startDate')();

  expect(node).toIncludeText('DateFilter');
});

it('should contain an edit filter modal when a filter should be edited', () => {
  const node = shallow(<Filter {...props} data={[{type: 'startDate'}]} />);

  node.instance().openEditFilterModal({
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'startDate',
  })();

  expect(node).toIncludeText('DateFilter');
});

it('should contain a FilterModal component based on the selected new Filter', () => {
  const node = shallow(<Filter {...props} />);

  node.instance().openNewFilterModal('variable')();

  expect(node).toIncludeText('VariableFilter');
  expect(node).not.toIncludeText('DateFilter');
});

it('should contain a EditFilterModal component based on the Filter selected for edition', () => {
  const node = shallow(<Filter {...props} data={[{type: 'variable'}]} />);

  node.instance().openEditFilterModal({
    data: {
      operator: 'bar',
      type: 'baz',
      value: 'foo',
    },
    type: 'variable',
  })();
  expect(node).toIncludeText('VariableFilter');
  expect(node).not.toIncludeText('DateFilter');
});

it('should add a filter to the list of filters', () => {
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

  node.instance().addFilter({type: 'Filter 2'});

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

it('should remove any previous startDate filters when adding a new date filter', () => {
  const spy = jest.fn();
  const previousFilters = [{type: 'startDate'}];

  const node = shallow(<Filter {...props} data={previousFilters} onChange={spy} />);

  node.instance().addFilter({type: 'startDate', value: 'new date'});

  expect(spy.mock.calls[0][0].filter).toEqual({
    $set: [{type: 'startDate', value: 'new date', filterLevel: 'instance'}],
  });
});

it('should remove any completed instances only filters when adding a new completed instances only filter', () => {
  const spy = jest.fn();
  const previousFilters = [{type: 'completedInstancesOnly'}];

  const node = shallow(<Filter {...props} data={previousFilters} onChange={spy} />);

  node.instance().addFilter({type: 'completedInstancesOnly'});

  expect(spy.mock.calls[0][0].filter).toEqual({
    $set: [{type: 'completedInstancesOnly', filterLevel: 'instance'}],
  });
});

it('should remove any running instances only filters when adding a new running instances only filter', () => {
  const spy = jest.fn();
  const previousFilters = [{type: 'runningInstancesOnly'}];

  const node = shallow(<Filter {...props} data={previousFilters} onChange={spy} />);

  node.instance().addFilter({type: 'runningInstancesOnly'});

  expect(spy.mock.calls[0][0].filter).toEqual({
    $set: [{type: 'runningInstancesOnly', filterLevel: 'instance'}],
  });
});

it('should render available filters depending on the provided filter level', () => {
  const node = shallow(<Filter {...props} filterLevel="view" />);

  expect(node.find('InstanceFilters')).not.toExist();
  expect(node.find('ViewFilters')).toExist();
});
