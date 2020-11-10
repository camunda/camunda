/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import SelectionFilter from './SelectionFilter';
import DateFilter from './DateFilter';
import BooleanFilter from './BooleanFilter';

import VariableFilter from './VariableFilter';

const props = {
  filter: null,
  config: {type: 'String', data: {}},
  setFilter: jest.fn(),
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should render component based on variable type', () => {
  const node = shallow(<VariableFilter {...props} />);

  expect(node.find(SelectionFilter)).toExist();
  expect(node.find(DateFilter)).not.toExist();
  expect(node.find(BooleanFilter)).not.toExist();

  node.setProps({config: {type: 'Date', data: {}}});
  expect(node.find(SelectionFilter)).not.toExist();
  expect(node.find(DateFilter)).toExist();
  expect(node.find(BooleanFilter)).not.toExist();

  node.setProps({config: {type: 'Boolean', data: {}}});
  expect(node.find(SelectionFilter)).not.toExist();
  expect(node.find(DateFilter)).not.toExist();
  expect(node.find(BooleanFilter)).toExist();
});

it('should render children', () => {
  const node = shallow(
    <VariableFilter {...props}>
      <div className="childContent" />
    </VariableFilter>
  );

  expect(node.find('.childContent')).toExist();
});
