/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {shallow} from 'enzyme';
import {parseISO} from 'date-fns';

import DateFields from './DateFields';
import DatePicker from './DatePicker';

console.error = jest.fn();

jest.mock('components', () => {
  return {
    ButtonGroup: (props: ComponentProps<'div'>) => <div {...props}>{props.children}</div>,
  };
});

const startDate = parseISO('2017-08-29');
const endDate = parseISO('2020-06-05');

it('should invok onDateChange prop function when a date change happens', () => {
  const spy = jest.fn();
  const node = shallow(
    <DatePicker onDateChange={spy} type="between" initialDates={{startDate, endDate}} />
  );
  node.find(DateFields).prop('onDateChange')('startDate', '2018-09-01');
  expect(spy).toHaveBeenCalledWith({startDate: parseISO('2018-09-01'), endDate, valid: true});
});

it('should set valid state to false when startDate or endDate is invalid', () => {
  const spy = jest.fn();
  const node = shallow(
    <DatePicker type="between" onDateChange={spy} initialDates={{startDate, endDate}} />
  );
  node.find(DateFields).prop('onDateChange')('startDate', 'invalidDate');
  expect(spy.mock.calls[0][0].valid).not.toBe(true);
});

it('should set startDate or endDate to null if type is before or after', () => {
  const spy = jest.fn();
  const node = shallow(
    <DatePicker onDateChange={spy} type="after" initialDates={{startDate, endDate}} />
  );
  node.find(DateFields).prop('onDateChange')('startDate', '2018-09-01');
  expect(spy).toHaveBeenCalledWith({startDate: parseISO('2018-09-01'), endDate: null, valid: true});
});
