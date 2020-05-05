/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown, DatePicker} from 'components';

import DateFilter from './DateFilter';

const props = {
  filter: null,
  setFilter: jest.fn(),
  type: 'startDate',
};

const todayFilter = {
  type: 'rolling',
  start: {value: 0, unit: 'days'},
  end: null,
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should contain a dropdown to set startDateFilter', () => {
  const node = shallow(<DateFilter {...props} />);

  expect(node.find(Dropdown)).toExist();

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith(todayFilter);
});

it('should show a datepicker when switching to fixed state', () => {
  const node = shallow(<DateFilter {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(node.find(DatePicker)).toExist();
});

it('should show the filter state', () => {
  const node = shallow(<DateFilter {...props} filter={todayFilter} />);

  expect(node.find(Dropdown).prop('label')).toMatchSnapshot();
  expect(node.find(Dropdown.Option).at(1)).toHaveProp('checked', true);
});

it('should reset the filter state', () => {
  const node = shallow(<DateFilter {...props} filter={todayFilter} />);

  node.find(Dropdown.Option).last().simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith();
});

it('should disable the reset button if no filter is set', () => {
  const node = shallow(<DateFilter {...props} />);

  expect(node.find(Dropdown.Option).last()).toHaveProp('disabled', true);
});
