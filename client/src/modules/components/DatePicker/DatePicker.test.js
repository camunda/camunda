/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DatePicker from './DatePicker';
import {mount} from 'enzyme';
import moment from 'moment';

console.error = jest.fn();

jest.mock('./DateFields', () => props => `DateFields: props: ${Object.keys(props)}`);

jest.mock('components', () => {
  return {
    ButtonGroup: props => <div {...props}>{props.children}</div>
  };
});

const startDate = moment([2017, 8, 29]);
const endDate = moment([2020, 6, 5]);

it('should contain date fields', () => {
  const node = mount(<DatePicker initialDates={{startDate, endDate}} />);

  expect(node).toIncludeText('DateFields');
});

it('should invok onDateChange prop function when a date change happens', () => {
  const spy = jest.fn();
  const node = mount(<DatePicker onDateChange={spy} initialDates={{startDate, endDate}} />);
  node.instance().onDateChange('startDate', '2018-09-01');
  expect(spy).toBeCalled();
});

it('should set valid state to false when startDate or endDate is invalid', () => {
  const spy = jest.fn();
  const node = mount(<DatePicker onDateChange={spy} initialDates={{startDate, endDate}} />);
  expect(node.state().valid).toBe(true);
  node.instance().onDateChange('startDate', 'invalid date');
  expect(node.state().valid).toBe(false);
});
