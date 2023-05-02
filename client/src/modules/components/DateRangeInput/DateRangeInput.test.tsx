/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  dateTypeSelect(node).prop('onChange')('last');
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

  expect(node.find({error: true})).toExist();
});
