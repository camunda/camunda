/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import NavItem from './NavItem';

import {loadEntity} from 'services';

jest.mock('services', () => ({
  loadEntity: jest.fn()
}));

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: fn => fn
  };
});

it('renders without crashing', () => {
  shallow(<NavItem active="/foo" location={{pathname: '/foo'}} />);
});

it('should contain the provided name', () => {
  const node = mount(<NavItem name="SectionName" active="/foo" location={{pathname: '/foo'}} />);

  expect(node).toIncludeText('SectionName');
});

it('should contain a link to the provided destination', () => {
  const node = mount(<NavItem linksTo="/section" active="/foo" location={{pathname: '/foo'}} />);

  expect(node.find('a')).toHaveProp('href', '/section');
});

it('should set the active class if the location pathname matches headerItem paths', () => {
  const node = mount(<NavItem active="/dashboards/*" location={{pathname: '/dashboards/1'}} />);

  expect(node.find('.NavItem')).toHaveClassName('active');
});

it('should render a breadcrumbs links when specified', async () => {
  loadEntity.mockReturnValueOnce({name: 'dashboard'});
  loadEntity.mockReturnValueOnce({name: 'report'});

  const node = shallow(
    <NavItem
      name="testName"
      active={['/report/*', '/dashboard/*']}
      location={{pathname: '/dashboard/did/report/rid'}}
      breadcrumbsEntities={['dashboard', 'report']}
    />
  );

  await node.update();
  expect(loadEntity).toHaveBeenCalledWith('dashboard', 'did');
  expect(loadEntity).toHaveBeenCalledWith('report', 'rid');
  await node.update();

  expect(node).toMatchSnapshot();
});
