/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {StringInput} from './string';
import FilterInstance from './FilterInstance';

const testVar = {name: 'testVar', type: 'String'};
const props = {
  expanded: false,
  toggleExpanded: jest.fn(),
  onRemove: jest.fn(),
  filter: {},
  variables: [testVar],
  updateFilterData: jest.fn(),
  config: {},
  applyTo: [],
  filters: [],
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should select a variable from the list of available variables', () => {
  const node = shallow(<FilterInstance {...props} />);

  node.find('Typeahead').prop('onChange')(testVar.name + '_' + testVar.type);

  expect(props.updateFilterData).toHaveBeenCalledWith({
    data: StringInput.defaultFilter,
    name: testVar.name,
    type: testVar.type,
  });
});

it('should update filter data on input component change ', () => {
  const filterData = {operator: 'in', values: ['testValue']};
  const filter = {name: 'testVar', type: 'String', data: {}};
  const node = shallow(<FilterInstance {...props} filter={filter} />);

  node.find(StringInput).prop('changeFilter')(filterData);

  expect(props.updateFilterData).toHaveBeenCalledWith({
    data: filterData,
    name: filter.name,
    type: filter.type,
  });
});

it('should only show filter header if variable is selected', () => {
  const node = shallow(<FilterInstance {...props} />);

  expect(node.find('.sectionTitle')).not.toExist();

  node.setProps({filter: {...testVar, data: {}}});

  expect(node.find('.sectionTitle .highlighted')).toIncludeText(testVar.name);
});

it('should invoke toggleExpanded when clicking on the filter', () => {
  const node = shallow(<FilterInstance {...props} filter={{...testVar, data: {}}} />);

  node.find('.sectionTitle').simulate('click');

  expect(props.toggleExpanded).toHaveBeenCalled();
});

it('should invoke onRemove when clicking the remove button', () => {
  const node = shallow(
    <FilterInstance
      {...props}
      filter={{...testVar, data: {}}}
      filters={[
        {name: 'var1', type: 'String', data: {}},
        {name: 'var2', type: 'String', data: {}},
      ]}
      expanded
    />
  );

  node.find('.sectionTitle .removeButton').simulate('click', {stopPropagation: jest.fn()});

  expect(props.onRemove).toHaveBeenCalled();
});
