/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {default as NavItem, refreshBreadcrumbs} from './NavItem';

import {loadEntitiesNames} from './service';

jest.mock('./service', () => ({
  loadEntitiesNames: jest.fn().mockReturnValue({}),
}));

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: (fn) => fn,
  };
});

beforeEach(() => {
  jest.clearAllMocks();
});

it('renders without crashing', () => {
  shallow(<NavItem active="/foo" location={{pathname: '/foo'}} />);
});

it('should contain the provided name', () => {
  const node = shallow(<NavItem name="SectionName" active="/foo" location={{pathname: '/foo'}} />);

  expect(node.find('Link').dive()).toIncludeText('SectionName');
});

it('should contain a link to the provided destination', () => {
  const node = shallow(<NavItem linksTo="/section" active="/foo" location={{pathname: '/foo'}} />);

  expect(node.find('Link')).toHaveProp('to', '/section');
});

it('should set the active class if the location pathname matches headerItem paths', () => {
  const node = shallow(<NavItem active="/dashboards/*" location={{pathname: '/dashboards/1'}} />);

  expect(node.find('.NavItem Link')).toHaveClassName('cds--header__menu-item--current');
});

it('should render a breadcrumbs links when specified', async () => {
  loadEntitiesNames.mockReturnValueOnce({dashboardName: 'dashboard', reportName: 'report'});

  const node = shallow(
    <NavItem
      name="testName"
      active={['/report/*', '/dashboard/*']}
      location={{pathname: '/dashboard/did/report/rid'}}
      breadcrumbsEntities={[{entity: 'dashboard'}, {entity: 'report'}]}
    />
  );

  await node.update();
  expect(loadEntitiesNames).toHaveBeenCalledWith({dashboardId: 'did', reportId: 'rid'});

  expect(node.find({to: '/dashboard/did/'})).toExist();
  expect(node.find({to: '/dashboard/did/report/rid/'})).toExist();
});

it('should update breadcrumbs when requested', async () => {
  const node = shallow(
    <NavItem
      name="testName"
      active={['/report/*', '/dashboard/*']}
      location={{pathname: '/dashboard/did/report/rid'}}
      breadcrumbsEntities={[{entity: 'dashboard'}, {entity: 'report'}]}
    />
  );

  await node.update();
  loadEntitiesNames.mockClear();

  expect(loadEntitiesNames).not.toHaveBeenCalled();
  refreshBreadcrumbs();
  expect(loadEntitiesNames).toHaveBeenCalled();
});

it('should not invoke loadEntitiesNames if id of the entity contains new keyword', async () => {
  const node = shallow(
    <NavItem
      name="testName"
      active={['/report/*', '/dashboard/*']}
      location={{pathname: '/dashboard/did/report/new'}}
      breadcrumbsEntities={[{entity: 'dashboard'}, {entity: 'report'}]}
    />
  );

  await node.update();
  expect(loadEntitiesNames).toHaveBeenCalledWith({dashboardId: 'did'});

  loadEntitiesNames.mockClear();
  node.setProps({
    location: {pathname: '/dashboard/new', breadcrumbsEntities: [{entity: 'dashboard'}]},
  });
  refreshBreadcrumbs();
  expect(loadEntitiesNames).not.toHaveBeenCalledWith();
});

it('should filter out breadcrumbs with no name', async () => {
  loadEntitiesNames.mockReturnValueOnce({dashboardName: 'dashboard', reportName: null});
  const node = shallow(
    <NavItem
      name="testName"
      active={['/dashboard/*', '/report/*']}
      location={{pathname: '/dashboard/did/report/rid'}}
      breadcrumbsEntities={[{entity: 'dashboard'}, {entity: 'report'}]}
    />
  );
  await node.update();

  expect(loadEntitiesNames).toHaveBeenCalledWith({dashboardId: 'did', reportId: 'rid'});
  expect(node.find({to: '/dashboard/did/'})).toExist();
  expect(node.find({to: '/dashboard/did/report/rid/'})).not.toExist();
});

it('should not invoke loadEntitiesNames for instance preview dashboards', async () => {
  const node = shallow(
    <NavItem
      name="testName"
      active={['/dashboard/*']}
      location={{pathname: '/dashboard/instant/did'}}
      breadcrumbsEntities={[{entity: 'dashboard'}]}
    />
  );
  await node.update();

  expect(loadEntitiesNames).not.toHaveBeenCalledWith({dashboardId: 'did'});
});

it('should use entity url to build breadcrumbs', async () => {
  loadEntitiesNames.mockReturnValueOnce({testName: 'test', reportName: 'report'});
  const node = shallow(
    <NavItem
      name="testName"
      active={['/test/*', '/report/*']}
      location={{pathname: '/test/subUrl/tid/report/rid'}}
      breadcrumbsEntities={[{entity: 'test', entityUrl: 'test/subUrl'}, {entity: 'report'}]}
    />
  );

  await node.update();

  expect(loadEntitiesNames).toHaveBeenCalledWith({testId: 'tid', reportId: 'rid'});
  expect(node.find({to: '/test/subUrl/tid/'})).toExist();
  expect(node.find({to: '/test/subUrl/tid/report/rid/'})).toExist();
});

it('should not invoke loadEntitiesNames if entity url is not found in the pathname', async () => {
  const node = shallow(
    <NavItem
      name="testName"
      active={['/test/*']}
      location={{pathname: '/test/tid/report/rid'}}
      breadcrumbsEntities={[{entity: 'test', entityUrl: 'test/subUrl'}]}
    />
  );

  await node.update();
  expect(loadEntitiesNames).not.toHaveBeenCalled();
});
