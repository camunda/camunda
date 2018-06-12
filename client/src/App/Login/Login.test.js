import React from 'react';
import {shallow, mount} from 'enzyme';
import {Redirect} from 'react-router-dom';

import {resetResponseInterceptor} from 'modules/request';

import Login from './Login';
import * as api from './api';

jest.mock('modules/request');

/**
 * helper function to flush promises in queue
 */
function flushPromises() {
  return new Promise(resolve => setImmediate(resolve));
}

describe('Login', () => {
  it('should reset the response interceptor', () => {
    // given
    const wrapper = mount(<Login />);

    // then
    expect(resetResponseInterceptor).toBeCalled();
  });

  it('should render login form by default', () => {
    // given
    const wrapper = mount(<Login />);

    // then
    expect(wrapper.state('username')).toEqual('');
    expect(wrapper.state('password')).toEqual('');
    expect(wrapper.state('forceRedirect')).toBe(false);
    expect(wrapper.state('error')).toBeNull();
    expect(wrapper.find('form')).toHaveLength(1);
    expect(wrapper.find('input[type="text"]')).toHaveLength(1);
    expect(wrapper.find('input[type="password"]')).toHaveLength(1);
    expect(wrapper.find('input[type="submit"]')).toHaveLength(1);
    expect(wrapper).toMatchSnapshot();
  });

  it('should change state according to inputs change', () => {
    // given
    const wrapper = mount(<Login />);
    const username = 'foo';
    const password = 'bar';

    // when
    wrapper
      .find("input[name='username']")
      .simulate('change', {target: {name: 'username', value: username}});

    wrapper
      .find("input[name='password']")
      .simulate('change', {target: {name: 'password', value: password}});

    // then
    expect(wrapper.state('username')).toEqual(username);
    expect(wrapper.state('password')).toEqual(password);
  });

  it('should redirect to home page on successful login', async () => {
    // given
    const wrapper = shallow(<Login />);
    api.login = jest.fn().mockImplementation(() => Promise.resolve());
    const form = wrapper.dive().find('form');
    const username = wrapper.state('username');
    const password = wrapper.state('password');

    // when
    form.simulate('submit', {preventDefault: () => {}});
    expect(api.login).toBeCalledWith({username, password});
    await flushPromises();
    wrapper.update();
    const RedirectNode = wrapper.find(Redirect);
    expect(RedirectNode).toHaveLength(1);
    expect(RedirectNode.prop('to')).toBe('/');
    expect(wrapper).toMatchSnapshot();
  });

  it('should display an error on unsuccessful login', async () => {
    // given
    const wrapper = mount(<Login />);
    const form = wrapper.find('form');
    const error = 'Username and Password do not match';
    api.login = jest.fn().mockImplementation(() => Promise.reject(error));

    // when
    form.simulate('submit', {preventDefault: () => {}});
    await flushPromises();
    wrapper.update();
    expect(wrapper.state('error')).toEqual(error);
    expect(wrapper.text()).toContain(error);
    expect(wrapper).toMatchSnapshot();
  });
});
