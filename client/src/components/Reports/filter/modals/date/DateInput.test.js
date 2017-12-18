import React from 'react';
import moment from 'moment';
import {mount} from 'enzyme';

import DateInput from './DateInput';

it('should create a text input field', () => {
  const node = mount(<DateInput date={moment()} format='YYYY-MM-DD' />);

  expect(node.find('input')).toBePresent();
});

it('should have field with value equal to formated date', () => {
  const node = mount(<DateInput date={moment()} format='YYYY-MM-DD' />);

  expect(node.find('input')).toHaveValue(moment().format('YYYY-MM-DD'));
});

it('should trigger onDateChange callback when input changes to valid date', () => {
  const spy = jest.fn();
  const node = mount(<DateInput date={moment()} format='YYYY-MM-DD' onDateChange={spy} disableAddButton = {jest.fn()}/>);

  node.simulate('change', {
    target: {
      value: '2016-05-07'
    }
  });

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].format('YYYY-MM-DD')).toBe('2016-05-07');
});

it('should add error class to true when input changes to invalid date', () => {
  const spy = jest.fn();
  const node = mount(<DateInput date={moment()} format='YYYY-MM-DD' onDateChange={spy} disableAddButton = {jest.fn()}/>);

  node.simulate('change', {
    target: {
      value: '2016-05-0'
    }
  });

  expect(node.find('input')).toHaveClassName('DateInput--error');
});
