/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateButton from './DateButton';

import {mount} from 'enzyme';
import moment from 'moment';

jest.mock('components', () => {
  return {
    Button: props => <button onClick={props.onClick}>{props.children}</button>
  };
});

it('should contain a button', () => {
  const node = mount(<DateButton dateLabel="today" />);

  expect(node.find('button')).toExist();
});

it('should set label on element', () => {
  const node = mount(<DateButton dateLabel="today" />);

  expect(node).toIncludeText('Today');
});

it('should set dates on click', () => {
  const spy = jest.fn();
  const node = mount(<DateButton format="YYYY-MM-DD" dateLabel="today" setDates={spy} />);

  const today = moment();

  node.find('button').simulate('click');

  const {startDate, endDate} = spy.mock.calls[0][0];

  expect(startDate).toEqual(today.format('YYYY-MM-DD'));
  expect(endDate).toEqual(today.format('YYYY-MM-DD'));
});
