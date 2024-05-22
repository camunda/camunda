/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {Calendar, DateRange as ReactDateRange} from 'react-date-range';

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

it('pass start and end dates to date range component', () => {
  const node = shallow(<DateRange {...props} type="between" />);

  expect(node.find(ReactDateRange).prop('ranges')).toEqual([
    {startDate: props.startDate, endDate: props.endDate},
  ]);
});

it('pass startDate to Calendar if type is "after" and endDate if type is "before"', () => {
  const node = shallow(<DateRange {...props} type="after" />);

  expect(node.find(Calendar).prop('date')).toEqual(props.startDate);

  node.setProps({type: 'before'});

  expect(node.find(Calendar).prop('date')).toEqual(props.endDate);
});

it('invoke onChange with null endDate if type is "after"', () => {
  const spy = jest.fn();
  const node = shallow(<DateRange {...props} type="after" onDateChange={spy} />);

  const date = new Date();
  node.find(Calendar).prop('onChange')?.(date);

  expect(spy).toHaveBeenCalledWith({endDate: null, startDate: date});
});
