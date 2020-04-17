/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateFilter from './DateFilter';
import {shallow} from 'enzyme';

import {convertFilterToState, isValid} from './service';
isValid.mockReturnValue(true);

jest.mock('./service');

const props = {
  filterType: 'startDate',
  filterData: null,
};

it('should contain a modal', () => {
  const node = shallow(<DateFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should render preview if the filter is valid', async () => {
  convertFilterToState.mockReturnValue({dateType: 'yesterday'});
  const filter = {type: 'startDate', data: {type: 'relative', start: {value: '1', unit: 'days'}}};
  const node = shallow(<DateFilter {...props} filterData={filter} />);

  expect(convertFilterToState).toHaveBeenCalledWith(filter.data);

  expect(node.find('DateFilterPreview')).toExist();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const filter = {type: 'startDate', data: {type: 'relative', start: {value: '5', unit: 'days'}}};
  const node = shallow(<DateFilter {...props} addFilter={spy} filterData={filter} />);
  const addButton = node.find('[primary]');

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
