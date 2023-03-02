/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import {PlatformLogin} from './PlatformLogin';

import {login} from './service';

jest.mock('./service', () => {
  return {
    login: jest.fn().mockReturnValue({token: 'authToken'}),
  };
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('renders without crashing', () => {
  shallow(<PlatformLogin {...props} />);
});

it('should have entered values in the input fields', () => {
  const node = shallow(<PlatformLogin {...props} />);
  const input = 'asdf';
  const field = 'username';

  node.find(`[name="${field}"]`).simulate('change', {target: {value: input}});

  expect(node.find('[name="username"]')).toHaveValue(input);
});

it('should call the login function when submitting the form', async () => {
  const node = shallow(<PlatformLogin {...props} onLogin={jest.fn()} />);

  const username = 'david';
  const password = 'dennis';

  node.find(`[name="username"]`).simulate('change', {target: {value: username}});
  node.find(`[name="password"]`).simulate('change', {target: {value: password}});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(login).toHaveBeenCalledWith(username, password);
});

it('should call the onLogin callback after login', async () => {
  const spy = jest.fn();
  const node = shallow(<PlatformLogin {...props} onLogin={spy} />);

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalled();
});

it('should display error message on failed login', async () => {
  const mightFail = (promise, cb, err) => err({status: 400, message: 'test error'});
  const node = shallow(<PlatformLogin {...props} mightFail={mightFail} />);

  login.mockReturnValueOnce({errorMessage: 'Failed'});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node.find('MessageBox[type="error"]')).toExist();
});

it('should disable the login button when waiting for server response', () => {
  const node = shallow(<PlatformLogin {...props} mightFail={() => {}} onLogin={jest.fn()} />);

  node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node.find(Button)).toBeDisabled();
});
