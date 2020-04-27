/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import moment from 'moment';

import ModificationInfo from './ModificationInfo';

jest.mock('moment', () => jest.fn().mockReturnValue({format: () => 'formatted date'}));

it('should format the passed date and display the user name', () => {
  moment.mockClear();
  const node = shallow(<ModificationInfo user="userId" date="iso-date-string" />);

  expect(moment).toHaveBeenCalledWith('iso-date-string');
  expect(node).toMatchSnapshot();
});
