/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps} from 'react';
import {shallow} from 'enzyme';

import {FilterProps} from '../types';

import DateFilter from './DateFilter';
import {convertFilterToState, isValid} from './service';

(isValid as jest.Mock).mockReturnValue(true);

jest.mock('./service');

const props: ComponentProps<typeof DateFilter> = {
  filterType: 'instanceStartDate',
  filterLevel: 'instance',
  filterData: {appliedTo: [], data: {}, type: 'instanceStartDate'},
  definitions: [{identifier: 'definition'}],
  close: jest.fn(),
  addFilter: jest.fn(),
};

it('should contain a modal', () => {
  const node = shallow(<DateFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should render preview if the filter is valid', async () => {
  (convertFilterToState as jest.Mock).mockReturnValue({dateType: 'yesterday'});
  const filter: FilterProps<'instanceStartDate'>['filterData'] = {
    type: 'instanceStartDate',
    data: {type: 'relative', start: {value: '1', unit: 'days'}},
    appliedTo: ['definition'],
  };
  const node = shallow(<DateFilter {...props} filterData={filter} />);

  expect(convertFilterToState).toHaveBeenCalledWith(filter.data);

  expect(node.find('DateFilterPreview')).toExist();
});

it('should have a create filter button', () => {
  const spy = jest.fn();
  const filter: FilterProps<'instanceStartDate'>['filterData'] = {
    type: 'instanceStartDate',
    data: {type: 'rolling', start: {value: '5', unit: 'days'}},
    appliedTo: ['definition'],
  };
  const node = shallow(<DateFilter {...props} addFilter={spy} filterData={filter} />);
  const addButton = node.find('.confirm');

  addButton.simulate('click');

  expect(spy).toHaveBeenCalled();
});
