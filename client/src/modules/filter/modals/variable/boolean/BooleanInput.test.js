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

it('should add/remove values to the list of selected values', () => {
  const spy = jest.fn();
  const node = shallow(<BooleanInput {...props} changeFilter={spy} />);

  node.find('TypeaheadMultipleSelection').prop('toggleValue')(true, true);

  expect(spy).toHaveBeenCalledWith({values: [true]});

  node.find('TypeaheadMultipleSelection').prop('toggleValue')(true, false);
  node.find('TypeaheadMultipleSelection').prop('toggleValue')(null, true);

  expect(spy).toHaveBeenCalledWith({values: [null]});
});
