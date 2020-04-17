/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import moment from 'moment';

import DateInput from './DateInput';

<<<<<<< HEAD
jest.mock('components', () => {
  return {
    DatePicker: () => 'DatePicker',
  };
});

=======
>>>>>>> chore(DateInput): include static data type for existing date filter
const props = {
  setValid: jest.fn(),
  changeFilter: jest.fn(),
  filter: {
    startDate: 'start',
    endDate: 'end',
  },
};

const exampleFilter = {
  type: 'variable',
  data: {
    name: 'aVariableName',
    type: 'Date',
    filterForUndefined: false,
    data: {
      start: '2018-07-09T00:00:00',
      end: '2018-07-12T23:59:59',
<<<<<<< HEAD
=======
      type: 'fixed',
>>>>>>> chore(DateInput): include static data type for existing date filter
    },
  },
};

it('should show a DatePicker', () => {
  const node = shallow(<DateInput {...props} />);

  expect(node.find('DatePicker')).toExist();
});

it('should parse an existing filter', () => {
  const parsed = DateInput.parseFilter(exampleFilter);

  expect(parsed.startDate).toBeDefined();
  expect(parsed.endDate).toBeDefined();
});

it('should convert a start and end-date to two compatible variable filters', () => {
  const spy = jest.fn();
  DateInput.addFilter(
    spy,
    {name: 'aVariableName', type: 'Date'},
    {
      startDate: moment('2018-07-09'),
      endDate: moment('2018-07-12'),
    },
    false
  );

  expect(spy).toHaveBeenCalledWith(exampleFilter);
});
