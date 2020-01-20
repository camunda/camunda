/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateFilter from './DateFilter';
import {shallow} from 'enzyme';

import {convertFilterToState} from './service';

jest.mock('./service');

const props = {
  filterType: 'startDate',
  filterData: null
};

const dateTypeSelect = node => node.find('Select').at(0);
const unitSelect = node => node.find('Select').at(1);

it('should contain a modal', () => {
  const node = shallow(<DateFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should disable the unit selection when not selecting this or last', () => {
  const node = shallow(<DateFilter {...props} />);

  dateTypeSelect(node).prop('onChange')('today');

  expect(unitSelect(node).prop('disabled')).toBe(true);
});

it('should render preview if the filter is valid', async () => {
  convertFilterToState.mockReturnValue({dateType: 'yesterday'});
  const filter = {type: 'startDate', data: {type: 'relative', start: {value: '1', unit: 'days'}}};
  const node = shallow(<DateFilter {...props} filterData={filter} />);

  expect(convertFilterToState).toHaveBeenCalledWith(filter.data);

  expect(node.find('DateFilterPreview')).toExist();
});

it('should reset the unit selection when changing the date type', () => {
  const node = shallow(<DateFilter {...props} />);
  dateTypeSelect(node).prop('onChange')('last');
  unitSelect(node).prop('onChange')('custom');
  dateTypeSelect(node).prop('onChange')('last');
  expect(unitSelect(node).prop('value')).toBe('');
});

it('should have isInvalid prop on the input if value is invalid', async () => {
  const node = shallow(<DateFilter {...props} />);
  dateTypeSelect(node).prop('onChange')('last');
  unitSelect(node).prop('onChange')('custom');
  node.find('.number').simulate('change', {target: {value: '-1'}});

  expect(node.find({error: true})).toExist();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const filter = {type: 'startDate', data: {type: 'relative', start: {value: '5', unit: 'days'}}};
  const node = shallow(<DateFilter {...props} addFilter={spy} filterData={filter} />);
  const addButton = node.find({variant: 'primary'});

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
