/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import DateRange from './DateRange';

import {shallow} from 'enzyme';

const props = {
  startDate: moment.utc('2012-12-15'),
  endDate: moment.utc('2018-05-02'),
  onDateChange: jest.fn()
};

it('match snapshot', () => {
  const node = shallow(<DateRange {...props} />);

  expect(node).toMatchSnapshot();
});
