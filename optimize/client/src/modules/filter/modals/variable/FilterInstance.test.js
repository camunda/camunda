/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {InlineNotification, Tag, TextInputSkeleton} from '@carbon/react';

import {StringInput} from './string';
import FilterInstance from './FilterInstance';

const testVar = {name: 'testVar', type: 'String'};
const testFilter = {...testVar, data: StringInput.defaultFilter};
const props = {
  expanded: false,
  toggleExpanded: jest.fn(),
  onRemove: jest.fn(),
  filter: {},
  variables: [{...testVar, label: 'testVarLabel'}],
  updateFilterData: jest.fn(),
  config: {},
  applyTo: [],
  filters: [],
  filterIdx: 0,
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should select a variable from the list of available variables', () => {
  const node = shallow(<FilterInstance {...props} />);
  runAllEffects();

  node.find('ComboBox').prop('onChange')({selectedItem: testVar});

  expect(props.updateFilterData).toHaveBeenCalledWith({
    data: StringInput.defaultFilter,
    name: testVar.name,
    type: testVar.type,
  });
});

it('should update filter data on input component change ', () => {
  const filterData = {operator: 'in', values: ['testValue']};
  const node = shallow(<FilterInstance {...props} filter={testFilter} />);
  runAllEffects();

  node.find(StringInput).prop('changeFilter')(filterData);

  expect(props.updateFilterData).toHaveBeenCalledWith({
    data: filterData,
    name: testFilter.name,
    type: testFilter.type,
  });
});

it('should show the header of the filter if there exists a filter after it', () => {
  const node = shallow(<FilterInstance {...props} />);

  runAllEffects();
  expect(node.find('.sectionTitle')).not.toExist();

  node.setProps({filter: testFilter, filters: [testFilter, {}]});
  runAllEffects();

  expect(node.find('.sectionTitle').find(Tag)).toIncludeText(props.variables[0].label);
});

it('should show the filter header on the last collapsed filter', () => {
  const validFilter = {name: 'testVar2', type: 'String', data: {values: ['a']}};
  const node = shallow(
    <FilterInstance
      {...props}
      filterIdx={1}
      filters={[testFilter, validFilter]}
      filter={validFilter}
    />
  );
  runAllEffects();

  expect(node.find('.sectionTitle').find(Tag)).toIncludeText(validFilter.name);
});

it('should prevent collapsing the invalid filter', () => {
  const invalidFilter = {name: 'testVar2', type: 'String', data: {values: []}};
  const node = shallow(
    <FilterInstance
      {...props}
      filterIdx={1}
      filters={[invalidFilter, testFilter]}
      filter={invalidFilter}
    />
  );
  runAllEffects();

  expect(node).not.toHaveClassName('collapsed');
  expect(node.find('.sectionToggle')).not.toExist();
});

it('should invoke toggleExpanded when clicking on a valid filter', () => {
  const validFilter = {name: 'testVar2', type: 'String', data: {values: ['a']}};
  const node = shallow(
    <FilterInstance {...props} filter={validFilter} filters={[validFilter, {}]} />
  );
  runAllEffects();

  node.find('.sectionTitle').simulate('click');

  expect(props.toggleExpanded).toHaveBeenCalled();
});

it('should invoke onRemove when clicking the remove button', () => {
  const node = shallow(
    <FilterInstance
      {...props}
      filter={testFilter}
      filters={[
        {name: 'var1', type: 'String', data: {}},
        {name: 'var2', type: 'String', data: {}},
      ]}
      expanded
    />
  );
  runAllEffects();

  node.find('.sectionTitle .removeButton').simulate('click', {stopPropagation: jest.fn()});

  expect(props.onRemove).toHaveBeenCalled();
});

it('should display a warning if there are no variables found', () => {
  const node = shallow(<FilterInstance {...props} variables={[]} />);

  runAllEffects();

  expect(node.find(InlineNotification).prop('subtitle')).toBe('No variables found');
});

it('should display a loading state while loading the data', () => {
  const node = shallow(<FilterInstance {...props} variables={null} />);

  runAllEffects();

  expect(node.find(TextInputSkeleton)).toExist();
});
