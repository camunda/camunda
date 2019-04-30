/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import HeaderNavItem from './HeaderNavItem';

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: fn => fn
  };
});

it('renders without crashing', () => {
  shallow(<HeaderNavItem active="/foo" location={{pathname: '/foo'}} />);
});

it('should contain the provided name', () => {
  const node = mount(
    <HeaderNavItem name="SectionName" active="/foo" location={{pathname: '/foo'}} />
  );

  expect(node).toIncludeText('SectionName');
});

it('should contain a link to the provided destination', () => {
  const node = mount(
    <HeaderNavItem linksTo="/section" active="/foo" location={{pathname: '/foo'}} />
  );

  expect(node.find('a')).toHaveProp('href', '/section');
});

it('should set the active class if the location pathname matches headerItem paths', () => {
  const node = mount(
    <HeaderNavItem active="/dashboards/*" location={{pathname: '/dashboards/1'}} />
  );

  expect(node.find('.HeaderNav__item')).toHaveClassName('active');
});
