/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import BooleanInput from './BooleanInput';

import {shallow} from 'enzyme';

const props = {
  filter: BooleanInput.defaultFilter,
  setValid: jest.fn(),
};

it('should assume no value is selected per default', () => {
  expect(BooleanInput.defaultFilter.values).toEqual([]);
});

it('should update filter selected values on Checklist change', () => {
  const spy = jest.fn();
  const node = shallow(<BooleanInput {...props} changeFilter={spy} />);

  node.find('Checklist').prop('onChange')([null, true]);

  expect(spy).toHaveBeenCalledWith({values: [null, true]});
});
