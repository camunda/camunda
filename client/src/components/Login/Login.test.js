/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Login from './Login';

import {login} from './service';

jest.mock('./service', () => {
  return {
    login: jest.fn()
  };
});
jest.mock('react-router-dom', () => {
  return {
    Redirect: ({to}) => {
      return <div>REDIRECT {to}</div>;
    }
  };
});

it('renders without crashing', () => {
  mount(<Login />);
});

it('should reflect the state in the input fields', () => {
  const node = mount(<Login />);
  const input = 'asdf';

  node.setState({username: input});

  expect(node.find('input[type="text"]')).toHaveValue(input);
});

it('should update the state from the input fields', () => {
  const node = mount(<Login />);
  const input = 'asdf';
  const field = 'username';

  node.instance().passwordField = document.createElement('input');

  node.find(`input[name="${field}"]`).simulate('change', {target: {value: input, name: field}});

  expect(node).toHaveState(field, input);
});

it('should call the login function when submitting the form', async () => {
  const node = mount(<Login />);

  node.instance().passwordField = document.createElement('input');

  const username = 'david';
  const password = 'dennis';

  node.setState({username, password});
  login.mockReturnValueOnce({token: '4mfio34nfinN93Jk9'});

  await node.find('button').simulate('click');

  expect(login).toHaveBeenCalledWith(username, password);
});

it('should redirect to the previous location after login', async () => {
  const node = mount(<Login location={{state: {from: '/protected'}}} />);

  login.mockReturnValueOnce({token: '4mfio34nfinN93Jk9'});

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT /protected');
});

it('should redirect to home after login if no previous page is given', async () => {
  const node = mount(<Login />);

  login.mockReturnValueOnce({token: '4mfio34nfinN93Jk9'});

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT /');
});

// re-enable these tests once https://github.com/airbnb/enzyme/issues/1604 is fixed
// it('should display the error message if there is an error', () => {
//   const node = mount(<Login />);

//   node.instance().passwordField = document.createElement('input');

//   node.setState({error: true});

//   expect(node.find('.Message--error')).toBePresent();
// });

// it('should set the error property on failed login', async () => {
//   const node = mount(<Login />);

//   node.instance().passwordField = document.createElement('input');

//   login.mockReturnValueOnce(false);

//   await node.find('button').simulate('click');

//   expect(node).toHaveState('error', true);
// });

// it('should clear the error state after user input', () => {
//   const node = mount(<Login />);

//   node.instance().passwordField = document.createElement('input');

//   node.setState({error: true});

//   const input = 'asdf';
//   const field = 'username';
//   node.find(`input[name="${field}"]`).simulate('change', {target: {value: input, name: field}});

//   expect(node).not.toHaveState('error', true);
// });
