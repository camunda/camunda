import React from 'react';
import {shallow} from 'enzyme';
import {Redirect} from 'react-router-dom';

import {
  flushPromises,
  mockResolvedAsyncFn,
  mockRejectedAsyncFn
} from 'modules/testUtils';

import Login from './Login';
import * as Styled from './styled';
import * as api from './api';

jest.mock('modules/request');

describe('Login', () => {
  let node;

  beforeEach(() => {
    node = shallow(<Login location={{}} />);
  });

  it('should render login form by default', () => {
    // given
    const usernameInput = node.findWhere(
      element =>
        element.prop('type') === 'text' && element.prop('name') === 'username'
    );
    const passwordInput = node.findWhere(
      element =>
        element.prop('type') === 'password' &&
        element.prop('name') === 'password'
    );
    const submitInput = node.findWhere(
      element => element.prop('type') === 'submit'
    );

    // then
    expect(node.state('username')).toEqual('');
    expect(node.state('password')).toEqual('');
    expect(node.state('forceRedirect')).toBe(false);
    expect(node.state('error')).toBeNull();
    expect(node.find(Styled.Login)).toHaveLength(1);
    expect(usernameInput).toHaveLength(1);
    expect(passwordInput).toHaveLength(1);
    expect(submitInput).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should change state according to inputs change', () => {
    // given
    const usernameInput = node.findWhere(
      element =>
        element.prop('type') === 'text' && element.prop('name') === 'username'
    );
    const passwordInput = node.findWhere(
      element =>
        element.prop('type') === 'password' &&
        element.prop('name') === 'password'
    );

    const username = 'foo';
    const password = 'bar';

    // when
    usernameInput.simulate('change', {
      target: {name: 'username', value: username}
    });

    passwordInput.simulate('change', {
      target: {name: 'password', value: password}
    });

    // then
    expect(node.state('username')).toEqual(username);
    expect(node.state('password')).toEqual(password);
  });

  describe('redirection', () => {
    let originalLogin = api.login;
    let username, password;
    api.login = mockResolvedAsyncFn();

    beforeEach(() => {
      api.login.mockClear();
      username = node.state('username');
      password = node.state('password');
    });

    afterAll(() => {
      // reset api.login
      api.login = originalLogin.bind(api);
    });

    it('should redirect to home page on successful login', async () => {
      // when
      node.instance().handleLogin({preventDefault: () => {}});
      await flushPromises();
      node.update();

      // then
      expect(api.login).toBeCalledWith({username, password});
      const RedirectNode = node.find(Redirect);
      expect(RedirectNode).toHaveLength(1);
      expect(RedirectNode.prop('to')).toBe('/');
      expect(node).toMatchSnapshot();
    });

    it('should redirect to referrer page on successful login', async () => {
      // given
      const referrer = '/some/page';
      node.setProps({location: {state: {referrer}}});

      // when
      node.instance().handleLogin({preventDefault: () => {}});
      await flushPromises();
      node.update();

      // then
      expect(api.login).toBeCalledWith({username, password});
      const RedirectNode = node.find(Redirect);
      expect(RedirectNode).toHaveLength(1);
      expect(RedirectNode.prop('to')).toBe(referrer);
      expect(node).toMatchSnapshot();
    });
  });

  it('should display an error on unsuccessful login', async () => {
    // mock api.login
    const originalLogin = api.login;
    api.login = mockRejectedAsyncFn();

    // given
    const error = 'Username and Password do not match';

    // when
    node.instance().handleLogin({preventDefault: () => {}});
    await flushPromises();
    node.update();

    // then
    const errorSpan = node.find(Styled.FormError).render();
    expect(node.state('error')).toEqual(error);
    expect(errorSpan.text()).toContain(error);
    expect(node).toMatchSnapshot();

    // reset api.login
    api.login = originalLogin.bind(api);
  });
});
