import React from 'react';
import {mount} from 'enzyme';

import Login from './Login';

import {login} from './service';

jest.mock('credentials', () => {return {
  getToken: jest.fn()
}});
jest.mock('./service', () => {return {
  login: jest.fn()
}});
jest.mock('react-router-dom', () => {return {
  Redirect: ({to}) => {return <div>REDIRECT {to}</div>}
}});
jest.mock('components', () => {return {
  Message: ({type}) => {return <div className={"Message Message--" + type}></div>},
  Button: props => <button {...props}>{props.children}</button>,
  Input: props => <input onChange={props.onChange} type={props.type} name={props.name} value={props.value}/>,
  Logo: () => <div></div>
  }
});


it('renders without crashing', () => {
  mount(<Login />);
});

it('should reflect the state in the input fields', () => {
  const node = mount(<Login />);
  const input = 'asdf'

  node.setState({username: input});

  expect(node.find('input[type="text"]')).toHaveValue(input);
});

it('should update the state from the input fields', () => {
  const node = mount(<Login />);
  const input = 'asdf'
  const field = 'username';

  node.instance().passwordField = document.createElement('input');

  node.find(`input[name="${field}"]`).simulate('change', {target: {value: input, name:field}});

  expect(node).toHaveState(field, input);
});

it('should display the error message if there is an error', () => {
  const node = mount(<Login />);

  node.instance().passwordField = document.createElement('input');

  node.setState({error: true});


  expect(node.find('.Message--error')).toBePresent();
});

it('should call the login function when submitting the form', async () => {
  const node = mount(<Login />);

  node.instance().passwordField = document.createElement('input');

  const username = 'david';
  const password = 'dennis';

  node.setState({username, password});

  await node.find('button').simulate('click');

  expect(login).toHaveBeenCalledWith(username, password);
});

it('should redirect to the previous location after login', async () => {
  const node = mount(<Login location={{state: {from: '/protected'}}} />);

  login.mockReturnValueOnce(true);

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT /protected');
});

it('should redirect to home after login if no previous page is given', async () => {
  const node = mount(<Login />);

  login.mockReturnValueOnce(true);

  await node.find('button').simulate('click');

  expect(node).toIncludeText('REDIRECT /');
});

it('should set the error property on failed login', async () => {
  const node = mount(<Login />);

  node.instance().passwordField = document.createElement('input');

  login.mockReturnValueOnce(false);

  await node.find('button').simulate('click');

  expect(node).toHaveState('error', true);
});

it('should clear the error state after user input', () => {
  const node = mount(<Login />);

  node.instance().passwordField = document.createElement('input');

  node.setState({error: true});

  const input = 'asdf'
  const field = 'username';
  node.find(`input[name="${field}"]`).simulate('change', {target: {value: input, name:field}});

  expect(node).not.toHaveState('error', true);
});
