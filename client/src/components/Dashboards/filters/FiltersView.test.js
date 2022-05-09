/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import VariableFilter from './VariableFilter';
import AssigneeFilter from './AssigneeFilter';

import FiltersView from './FiltersView';

const props = {
  availableFilters: [],
  setFilter: jest.fn(),
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should render a filter input based on the availableFilters', () => {
  const node = shallow(<FiltersView availableFilters={[{type: 'state'}]} />);

  expect(node.find('InstanceStateFilter')).toExist();
});

it('should pass a single filter to the date filter component', () => {
  const dateFilter = {
    type: 'relative',
    start: {value: 0, unit: 'days'},
    end: null,
  };
  const node = shallow(
    <FiltersView
      availableFilters={[{type: 'state'}, {type: 'instanceStartDate'}]}
      filter={[{type: 'runningInstancesOnly'}, {type: 'instanceStartDate', data: dateFilter}]}
    />
  );

  expect(node.find('DateFilter')).toHaveProp('filter', dateFilter);
});

it('should have a button to reset all filters', () => {
  const node = shallow(<FiltersView {...props} />);

  node.find(Button).simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith([]);
});

it('should pass a resetTrigger to Assignee and Variable Filters to help manage internal state', () => {
  const node = shallow(
    <FiltersView
      {...props}
      availableFilters={[
        {type: 'assignee', data: {operator: 'in', values: ['user']}},
        {type: 'variable', data: {name: 'boolVar', type: 'Boolean'}, filterLevel: 'instance'},
      ]}
    />
  );

  node.find(Button).simulate('click');

  expect(node.find(AssigneeFilter)).toHaveProp('resetTrigger', true);
  expect(node.find(VariableFilter)).toHaveProp('resetTrigger', true);
});

it('should add a variable filter', () => {
  const node = shallow(
    <FiltersView
      {...props}
      availableFilters={[
        {type: 'variable', data: {name: 'boolVar', type: 'Boolean'}, filterLevel: 'instance'},
      ]}
    />
  );

  const variableFilter = node.find(VariableFilter);
  expect(variableFilter).toExist();
  expect(variableFilter.prop('filter')).toBe(undefined);
  expect(variableFilter.prop('config')).toEqual({name: 'boolVar', type: 'Boolean'});

  variableFilter.prop('setFilter')({values: [true]});
  const expectedResult = [
    {
      type: 'variable',
      data: {name: 'boolVar', type: 'Boolean', data: {values: [true]}},
      filterLevel: 'instance',
    },
  ];
  expect(props.setFilter).toHaveBeenCalledWith(expectedResult);

  node.setProps({filter: expectedResult});
  expect(node.find(VariableFilter).prop('filter')).toEqual({values: [true]});
});

it('should remove a variable filter', () => {
  const node = shallow(
    <FiltersView
      {...props}
      availableFilters={[
        {type: 'runningInstancesOnly'},
        {type: 'variable', data: {name: 'boolVar', type: 'Boolean'}},
      ]}
      filter={[
        {type: 'runningInstancesOnly'},
        {type: 'variable', data: {name: 'boolVar', type: 'Boolean', data: {values: [true]}}},
      ]}
    />
  );

  node.find(VariableFilter).prop('setFilter')();

  expect(props.setFilter).toHaveBeenCalledWith([{type: 'runningInstancesOnly'}]);
});
