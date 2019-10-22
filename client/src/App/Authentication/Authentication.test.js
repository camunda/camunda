/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {shallow} from 'enzyme';

import * as request from 'modules/request/request';
import * as wrappers from 'modules/request/wrappers';

import AuthenticationWithRouter from './Authentication';
import {mockResolvedAsyncFn} from 'modules/testUtils';

const {WrappedComponent: Authentication} = AuthenticationWithRouter;

describe('Authentication', () => {
  request.setResponseInterceptor = jest.fn();
  wrappers.get = mockResolvedAsyncFn({status: 200});

  let Child, node;
  const mockLocation = {pathname: '/some/page'};

  beforeEach(() => {
    request.setResponseInterceptor.mockClear();
    wrappers.get.mockClear();

    Child = () => <span>I am a child component</span>;
    node = shallow(
      <Authentication location={mockLocation}>
        <Child />
      </Authentication>
    );
  });

  it('should attach a responseInterceptor', () => {
    expect(request.setResponseInterceptor).toBeCalled();
  });

  it('should render children by default', async () => {
    expect(node.state('forceRedirect')).toBe(false);
    expect(node.find(Child)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it('should redirect to login when forceRedirect is true', () => {
    // given
    const expectedTo = {
      pathname: '/login',
      state: {referrer: mockLocation.pathname}
    };

    // when
    node.setState({forceRedirect: true});

    // then
    expect(node.find(Child)).toHaveLength(0);
    const RedirectNode = node.find(Redirect);
    expect(RedirectNode).toHaveLength(1);
    expect(RedirectNode.prop('to')).toEqual(expectedTo);
    expect(node).toMatchSnapshot();
  });

  it('should redirect to login when check login status failed', () => {
    // given
    const expectedTo = {
      pathname: '/login',
      state: {referrer: mockLocation.pathname}
    };

    // mock resetState so we can catch the forceRedirect change in state
    node.instance().disableForceRedirect = jest.fn();

    // when
    node.instance().checkLoginStatus(401);

    // then
    expect(node.find(Child)).toHaveLength(0);
    const RedirectNode = node.find(Redirect);
    expect(RedirectNode).toHaveLength(1);
    expect(RedirectNode.prop('to')).toEqual(expectedTo);
    expect(node).toMatchSnapshot();
  });

  it('should set forceRedirect to true on failed response', async () => {
    // given
    // mock resetState so we can catch the forceRedirect change in state
    node.instance().disableForceRedirect = jest.fn();

    // when
    node.instance().interceptResponse({status: 401});

    // then
    expect(node.state().forceRedirect).toBe(true);
    expect(node.instance().disableForceRedirect).toBeCalled();
  });

  it('should reset forceRedirect to false when disableForceRedirect is called', () => {
    // when
    node.setState({forceRedirect: true});
    node.instance().disableForceRedirect();

    // then
    expect(node.state('forceRedirect')).toBe(false);
  });
});
