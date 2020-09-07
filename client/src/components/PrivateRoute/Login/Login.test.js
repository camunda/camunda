/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Login from './Login';

import {login} from './service';

jest.mock('./service', () => {
  return {
    login: jest.fn().mockReturnValue({token: 'authToken'}),
  };
});

it('renders without crashing', () => {
  shallow(<Login />);
});

it('should have entered values in the input fields', () => {
  const node = shallow(<Login />);
  const input = 'asdf';
  const field = 'username';

  node.find(`[name="${field}"]`).simulate('change', {target: {value: input}});

  expect(node.find('[name="username"]')).toHaveValue(input);
});

it('should call the login function when submitting the form', async () => {
  const node = shallow(<Login onLogin={jest.fn()} />);

  const username = 'david';
  const password = 'dennis';

  node.find(`[name="username"]`).simulate('change', {target: {value: username}});
  node.find(`[name="password"]`).simulate('change', {target: {value: password}});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(login).toHaveBeenCalledWith(username, password);
});

it('should call the onLogin callback after login', async () => {
  const spy = jest.fn();
  const node = shallow(<Login onLogin={spy} />);

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalled();
});

it('should display error message on failed login', async () => {
  const node = shallow(<Login />);

  login.mockReturnValueOnce({errorMessage: 'Failed'});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node.find('MessageBox[type="error"]')).toExist();
});

it('should disable the login button when waiting for server response', () => {
  const node = shallow(<Login onLogin={jest.fn()} />);

  node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node.find(Button)).toBeDisabled();
});
