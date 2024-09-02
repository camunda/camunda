/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {RadioButton} from '@carbon/react';

import BooleanFilter from './BooleanFilter';

const props = {
  filter: null,
  setFilter: jest.fn(),
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should filter for boolean', () => {
  const node = shallow(<BooleanFilter {...props} />);

  node.find(RadioButton).first().simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith({values: [true]});
});
