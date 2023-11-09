/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {InlineNotification, Button} from '@carbon/react';

import {useErrorHandling} from 'hooks';

import {PlatformLogin} from './PlatformLogin';

import {login} from './service';

jest.mock('./service', () => {
  return {
    login: jest.fn().mockReturnValue({token: 'authToken'}),
  };
});

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
}));

const props = {
  onLogin: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

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
  const node = shallow(<PlatformLogin {...props} />);

  const username = 'david';
  const password = 'dennis';

  node.find(`[name="username"]`).simulate('change', {target: {value: username}});
  node.find(`[name="password"]`).simulate('change', {target: {value: password}});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(login).toHaveBeenCalledWith(username, password);
});

it('should call the onLogin callback after login', async () => {
  const node = shallow(<PlatformLogin {...props} />);

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(props.onLogin).toHaveBeenCalled();
});

it('should display error message on failed login', async () => {
  (useErrorHandling as jest.Mock).mockImplementation(() => ({
    mightFail: (promise: any, cb: any, err: any) => err({status: 400, message: 'test error'}),
  }));

  const node = shallow(<PlatformLogin {...props} />);

  (login as jest.Mock).mockReturnValueOnce({errorMessage: 'Failed'});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node.find(InlineNotification)).toExist();
});

it('should disable the login button when waiting for server response', () => {
  const node = shallow(<PlatformLogin {...props} />);

  node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node.find(Button)).toBeDisabled();
});
