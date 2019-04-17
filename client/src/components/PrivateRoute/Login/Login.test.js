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
    login: jest.fn()
  };
});

it('renders without crashing', () => {
  shallow(<Login />);
});

it('should reflect the state in the input fields', () => {
  const node = shallow(<Login />);
  const input = 'asdf';

  node.setState({username: input});

  expect(node.find('[name="username"]')).toHaveValue(input);
});

it('should update the state from the input fields', () => {
  const node = shallow(<Login />);
  const input = 'asdf';
  const field = 'username';

  node.find(`[name="${field}"]`).simulate('change', {target: {value: input, name: field}});

  expect(node).toHaveState(field, input);
});

it('should call the login function when submitting the form', async () => {
  const node = shallow(<Login onLogin={jest.fn()} />);

  const username = 'david';
  const password = 'dennis';

  node.setState({username, password});
  login.mockReturnValueOnce({token: '4mfio34nfinN93Jk9'});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(login).toHaveBeenCalledWith(username, password);
});

it('should call the onLogin callback after login', async () => {
  const spy = jest.fn();
  const node = shallow(<Login onLogin={spy} />);

  login.mockReturnValueOnce({token: '4mfio34nfinN93Jk9'});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(spy).toHaveBeenCalled();
});

it('should display the error message if there is an error', () => {
  const node = shallow(<Login />);

  node.setState({error: true});

  expect(node.find('Message[type="error"]')).toBePresent();
});

it('should set the error property on failed login', async () => {
  const node = shallow(<Login />);

  login.mockReturnValueOnce({errorMessage: 'Failed'});

  await node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node).toHaveState('error', 'Failed');
});
