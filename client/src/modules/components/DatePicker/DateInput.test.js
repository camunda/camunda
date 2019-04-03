/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// re-enable the tests once https://github.com/airbnb/enzyme/issues/1604 is fixed

// import React from 'react';
// import moment from 'moment';
// import {mount} from 'enzyme';

// import DateInput from './DateInput';

// it('should create a text input field', () => {
//   const node = mount(<DateInput date={moment()} format="YYYY-MM-DD" />);

//   expect(node.find('input')).toBePresent();
// });

// it('should have field with value equal to formated date', () => {
//   const node = mount(<DateInput date={moment()} format="YYYY-MM-DD" />);

//   expect(node.find('input')).toHaveValue(moment().format('YYYY-MM-DD'));
// });

// it('should trigger onDateChange callback when input changes to valid date', () => {
//   const spy = jest.fn();
//   const node = mount(
//     <DateInput date={moment()} format="YYYY-MM-DD" onDateChange={spy} enableAddButton={jest.fn()} />
//   );

//   node.find('Input').simulate('change', {
//     target: {
//       value: '2016-05-07'
//     }
//   });

//   expect(spy).toHaveBeenCalled();
//   expect(spy.mock.calls[0][0].format('YYYY-MM-DD')).toBe('2016-05-07');
// });

// it('should add isInvalid prop to true when input changes to invalid date', () => {
//   const spy = jest.fn();
//   const node = mount(
//     <DateInput date={moment()} format="YYYY-MM-DD" onDateChange={spy} enableAddButton={jest.fn()} />
//   );

//   node.find('Input').simulate('change', {
//     target: {
//       value: '2016-05-0'
//     }
//   });

//   expect(node.find('Input').props()).toHaveProperty('isInvalid', true);
// });

it('has a test file', () => {});
