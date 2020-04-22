/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';

import {LogoutButton} from './LogoutButton';

const props = {
  mightFail: jest.fn(),
  history: {push: jest.fn()},
};

it('renders without crashing', () => {
  shallow(<LogoutButton />);
});

it('should go to the temporary logout route', () => {
  const node = shallow(<LogoutButton {...props} />);

  node.find(Dropdown.Option).simulate('click');
  expect(props.history.push).toHaveBeenCalledWith('/logout');
});
