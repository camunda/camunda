import React from 'react';
import {shallow, mount} from 'enzyme';
import {Redirect} from 'react-router-dom';

import {resetResponseInterceptor} from 'modules/request';
import {
  flushPromises,
  mockResolvedAsyncFn,
  mockRejectedAsyncFn
} from 'modules/testUtils';

import Login from './Login';
import * as api from './api';

jest.mock('modules/request');

describe('Login', () => {
  it('should reset the response interceptor', () => {
    // given
    mount(<Login />);

    // then
    expect(resetResponseInterceptor).toBeCalled();
  });

  it('should render login form by default', () => {
    // given
    const node = mount(<Login />);

    // then
    expect(node.state('username')).toEqual('');
    expect(node.state('password')).toEqual('');
    expect(node.state('forceRedirect')).toBe(false);
    expect(node.state('error')).toBeNull();
    expect(node.find('form')).toHaveLength(1);
    expect(node.find('input[type="text"]')).toHaveLength(1);
    expect(node.find('input[type="password"]')).toHaveLength(1);
    expect(node.find('input[type="submit"]')).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should change state according to inputs change', () => {
    // given
    const node = mount(<Login />);
    const username = 'foo';
    const password = 'bar';

    // when
    node
      .find("input[name='username']")
      .simulate('change', {target: {name: 'username', value: username}});

    node
      .find("input[name='password']")
      .simulate('change', {target: {name: 'password', value: password}});

    // then
    expect(node.state('username')).toEqual(username);
    expect(node.state('password')).toEqual(password);
  });

  it('should redirect to home page on successful login', async () => {
    // mock api.login
    const originalLogin = api.login;
    api.login = mockResolvedAsyncFn();

    // given
    const node = shallow(<Login />);
    const form = node.dive().find('form');
    const username = node.state('username');
    const password = node.state('password');

    // when
    form.simulate('submit', {preventDefault: () => {}});
    expect(api.login).toBeCalledWith({username, password});
    await flushPromises();
    node.update();
    const RedirectNode = node.find(Redirect);
    expect(RedirectNode).toHaveLength(1);
    expect(RedirectNode.prop('to')).toBe('/');
    expect(node).toMatchSnapshot();

    // reset api.login
    api.login = originalLogin.bind(api);
  });

  it('should display an error on unsuccessful login', async () => {
    // mock api.login
    const originalLogin = api.login;
    api.login = mockRejectedAsyncFn();

    // given
    const node = mount(<Login />);
    const form = node.find('form');
    const error = 'Username and Password do not match';

    // when
    form.simulate('submit', {preventDefault: () => {}});
    await flushPromises();
    node.update();
    expect(node.state('error')).toEqual(error);
    expect(node.text()).toContain(error);
    expect(node).toMatchSnapshot();

    // reset api.login
    api.login = originalLogin.bind(api);
  });
});
