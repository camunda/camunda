/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Dropdown, DatePicker} from 'components';

import DateFilter from './DateFilter';
import RollingFilter from './RollingFilter';

const props = {
  filter: null,
  setFilter: jest.fn(),
  type: 'instanceStartDate',
};

const todayFilter = {
  type: 'relative',
  start: {value: 0, unit: 'days'},
  end: null,
  includeUndefined: false,
  excludeUndefined: false,
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should contain a dropdown to set startDateFilter', () => {
  const node = shallow(<DateFilter {...props} />);

  expect(node.find(Dropdown)).toExist();

  node.find(Dropdown.Option).at(3).simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith(todayFilter);
});

it('should show a datepicker when switching to fixed state', () => {
  const node = shallow(<DateFilter {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(node.find(DatePicker)).toExist();
});

it('should invoke setFilter when updating the rolling filter', () => {
  const spy = jest.fn();
  const filter = {
    type: 'rolling',
    start: {value: 2, unit: 'days'},
    end: null,
    excludeUndefined: false,
    includeUndefined: false,
  };
  const node = shallow(<DateFilter {...props} setFilter={spy} />);

  node.setProps({filter});

  const options = node.find(Dropdown.Option);
  options.at(options.length - 2).simulate('click');
  expect(spy).toHaveBeenCalledWith(filter);

  node.find(RollingFilter).simulate('change', {value: '5', unit: 'months'});
  expect(spy).toHaveBeenCalledWith({...filter, start: {value: '5', unit: 'months'}});
});

it('should show the dropdown again after an external reset', () => {
  const node = shallow(<DateFilter {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');
  node.setProps({resetTrigger: true});

  runAllEffects();

  expect(node.find(DatePicker)).not.toExist();
  expect(node.find(Dropdown)).toExist();
});

it('should show the filter state', () => {
  const node = shallow(<DateFilter {...props} filter={todayFilter} />);

  expect(node.find(Dropdown).prop('label')).toMatchSnapshot();
  expect(node.find(Dropdown.Option).at(3)).toHaveProp('checked', true);
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

it('should allow providing a custom icon and empty text', () => {
  const node = shallow(<DateFilter {...props} icon="customIcon" emptyText="customText" />);

  expect(node.find(Dropdown).prop('label')).toMatchSnapshot();
});

it('should render children', () => {
  const node = shallow(
    <DateFilter {...props}>
      <div className="childContent" />
    </DateFilter>
  );

  expect(node.find('.childContent')).toExist();
});
