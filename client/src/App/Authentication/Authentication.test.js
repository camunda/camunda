/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {shallow} from 'enzyme';

import {setResponseInterceptor} from 'modules/request';

import AuthenticationWithRouter from './Authentication';

const {WrappedComponent: Authentication} = AuthenticationWithRouter;

jest.mock('modules/request');

describe('Authentication', () => {
  let Child, node;
  const mockLocation = {pathname: '/some/page'};

  beforeEach(() => {
    Child = () => <span>I am a child component</span>;
    node = shallow(
      <Authentication location={mockLocation}>
        <Child />
      </Authentication>
    );
  });

  it('should attach a responseInterceptor', () => {
    expect(setResponseInterceptor).toBeCalled();
  });

  it('should render children by default', () => {
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

  it('should set forceRedirect to true on failed response', () => {
    // given
    // mock resetState so we can catch the forceRedirect change in state
    node.instance().resetState = jest.fn();

    // when
    node.instance().interceptResponse({status: 401});

    // then
    expect(node.state().forceRedirect).toBe(true);
    expect(node.instance().resetState).toBeCalled();
  });

  it('should reset falseRedirect to false when resetState is called', () => {
    // when
    node.setState({forceRedirect: true});
    node.instance().resetState();

    // then
    expect(node.state('forceRedirect')).toBe(false);
  });
});
