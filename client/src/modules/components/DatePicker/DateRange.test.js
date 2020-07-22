/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import DateRange from './DateRange';

jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  isValid: () => true,
  isAfter: () => true,
}));

const props = {
  startDate: new Date('2019-01-01'),
  endDate: new Date('2020-01-05'),
  onDateChange: jest.fn(),
};

it('match snapshot', () => {
  const node = shallow(<DateRange {...props} />);

  expect(node).toMatchSnapshot();
});
