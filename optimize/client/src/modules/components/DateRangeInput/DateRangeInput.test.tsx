/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow, ShallowWrapper} from 'enzyme';

import {Select} from 'components';

import DateRangeInput from './DateRangeInput';

const props = {
  type: '',
  unit: '',
  customNum: '2',
  startDate: null,
  endDate: null,
  onChange: () => {},
};

const dateTypeSelect = (node: ShallowWrapper) => node.find(Select).at(0);
const unitSelect = (node: ShallowWrapper) => node.find(Select).at(1);

it('should disable the unit selection when not selecting this or last', () => {
  const node = shallow(<DateRangeInput {...props} type="today" />);

  expect(unitSelect(node).prop('disabled')).toBe(true);
});

it('should reset the unit selection when changing the date type', () => {
  const spy = jest.fn();
  const node = shallow(<DateRangeInput {...props} type="this" unit="weeks" onChange={spy} />);

  dateTypeSelect(node).prop('onChange')?.('last');
  expect(spy).toHaveBeenCalledWith({
    type: 'last',
    unit: '',
    startDate: null,
    endDate: null,
    valid: false,
  });
});

it('should have error message if value is invalid', async () => {
  const node = shallow(<DateRangeInput {...props} type="custom" unit="minutes" customNum="-1" />);

  expect(node.find('TextInput').prop('invalid')).toBe(true);
});
