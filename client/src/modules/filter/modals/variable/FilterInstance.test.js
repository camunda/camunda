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
  filter: {},
  variables: [testVar],
  updateFilterData: jest.fn(),
  config: {},
  applyTo: [],
  filters: [],
};

beforeEach(() => {
  props.updateFilterData.mockClear();
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
