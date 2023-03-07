/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

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
  const node = mount(<NavItem name="SectionName" active="/foo" location={{pathname: '/foo'}} />);

  expect(node).toIncludeText('SectionName');
});

it('should contain a link to the provided destination', () => {
  const node = mount(<NavItem linksTo="/section" active="/foo" location={{pathname: '/foo'}} />);

  expect(node.find('a')).toHaveProp('href', '/section');
});

it('should set the active class if the location pathname matches headerItem paths', () => {
  const node = mount(<NavItem active="/dashboards/*" location={{pathname: '/dashboards/1'}} />);

  expect(node.find('.NavItem Link')).toHaveClassName('active');
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

it('should override entity with the same name', async () => {
  loadEntitiesNames.mockReturnValueOnce({dashboardName: 'dashboard'});
  const node = shallow(
    <NavItem
      name="testName"
      active={['/dashboard/*']}
      location={{pathname: '/dashboard/instant/did'}}
      breadcrumbsEntities={[
        {entity: 'dashboard'},
        {entity: 'dashboard', entityUrl: 'dashboard/instant'},
      ]}
    />
  );
  await node.update();

  expect(loadEntitiesNames).toHaveBeenCalledWith({dashboardId: 'did'});
  expect(node.find({to: '/dashboard/instant/did/'})).toExist();
});

it('should use entity url to build breadcrumbs', async () => {
  loadEntitiesNames.mockReturnValueOnce({dashboardInstantName: 'dashboard', reportName: 'report'});
  const node = shallow(
    <NavItem
      name="testName"
      active={['/dashboard/*', '/report/*']}
      location={{pathname: '/dashboard/instant/did/report/rid'}}
      breadcrumbsEntities={[
        {entity: 'dashboardInstant', entityUrl: 'dashboard/instant'},
        {entity: 'report'},
      ]}
    />
  );
  await node.update();

  expect(loadEntitiesNames).toHaveBeenCalledWith({dashboardInstantId: 'did', reportId: 'rid'});
  expect(node.find({to: '/dashboard/instant/did/'})).toExist();
  expect(node.find({to: '/dashboard/instant/did/report/rid/'})).toExist();
});
