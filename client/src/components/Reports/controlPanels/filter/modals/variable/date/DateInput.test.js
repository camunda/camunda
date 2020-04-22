/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import moment from 'moment';
import update from 'immutability-helper';

import DateInput from './DateInput';

const props = {
  setValid: jest.fn(),
  changeFilter: jest.fn(),
  variable: {type: 'Date', name: 'aVariableName'},
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
      type: 'fixed',
    },
  },
};

it('should use the DateRangeInput', () => {
  const node = shallow(<DateInput {...props} />);

  expect(node.find('DateRangeInput')).toExist();
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
      valid: false,
      type: 'fixed',
      unit: '',
      customNum: '2',
      startDate: moment('2018-07-09'),
      endDate: moment('2018-07-12'),
    },
    false
  );

  expect(spy).toHaveBeenCalledWith(exampleFilter);
});

it('should show a date filter preview if the filter is valid', () => {
  const node = shallow(<DateInput {...props} filter={DateInput.parseFilter(exampleFilter)} />);

  expect(node.find('DateFilterPreview')).toExist();
});

it('should show a date filter preview even if the value should be undefined or null', () => {
  const nullFilter = update(exampleFilter, {data: {filterForUndefined: {$set: true}}});
  const node = shallow(
    <DateInput {...props} filter={DateInput.parseFilter(nullFilter)} disabled />
  );

  expect(node.find('.previewContainer')).toMatchSnapshot();
});
