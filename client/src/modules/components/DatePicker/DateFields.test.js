/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import DateFields from './DateFields';
import {shallow} from 'enzyme';
import DateRange from './DateRange';

jest.mock('./DateInput');
const format = 'YYYY-MM-DD';

const props = {
  startDate: moment('2017-08-29').format(format),
  endDate: moment('2020-06-05').format(format),
  format
};

it('should match snapshot', () => {
  const node = shallow(<DateFields {...props} />);

  expect(node).toMatchSnapshot();
});

it('should set startDate on date change of start date input field', () => {
  const spy = jest.fn();
  const node = shallow(<DateFields {...props} onDateChange={spy} />);

  node.instance().setDate('startDate')('change');

  expect(spy).toBeCalledWith('startDate', 'change');
});

it('should set endDate on date change of end date input field', () => {
  const spy = jest.fn();
  const node = shallow(<DateFields {...props} onDateChange={spy} />);

  node.instance().setDate('endDate')('change');

  expect(spy).toBeCalledWith('endDate', 'change');
});

it('should select date range popup on date input click', () => {
  const node = shallow(<DateFields {...props} enableAddButton={jest.fn()} />);

  const evt = {nativeEvent: {stopImmediatePropagation: jest.fn()}};
  node.instance().toggleDateRangeForStart(evt);

  expect(evt.nativeEvent.stopImmediatePropagation).toHaveBeenCalled();
  expect(node.state('popupOpen')).toBe(true);
  expect(node.state('currentlySelectedField')).toBe('startDate');
});

it('should have DateRange', () => {
  const node = shallow(<DateFields {...props} enableAddButton={jest.fn()} />);
  node.setState({popupOpen: true});

  expect(node.find(DateRange)).toExist();
});

it('should update start and end date when selecting a date', () => {
  const spy = jest.fn();
  const node = shallow(<DateFields {...props} onDateChange={spy} enableAddButton={jest.fn()} />);

  node.setState({popupOpen: true, currentlySelectedField: 'startDate'});

  node.instance().endDateField = {current: {focus: jest.fn()}};
  node.instance().onDateRangeChange({range1: {startDate: new Date(), endDate: new Date()}});
  const today = moment().format(format);
  expect(spy).toHaveBeenCalledWith('startDate', today);
  expect(spy).toHaveBeenCalledWith('endDate', today);
  expect(node.state('currentlySelectedField')).toBe('endDate');
});

it('should be possible to disable the date fields', () => {
  const node = shallow(<DateFields {...props} disabled />);

  const dateInputs = node.find('DateInput');
  expect(dateInputs.at(0).props('disabled')).toBeTruthy();
  expect(dateInputs.at(1).props('disabled')).toBeTruthy();
});
