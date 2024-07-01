/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
