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
  loadEntitiesNames: jest.fn(),
}));

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: (fn) => fn,
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

  expect(node.find('.NavItem Link')).toHaveClassName('active');
});

it('should render a breadcrumbs links when specified', async () => {
  loadEntitiesNames.mockReturnValue({dashboardName: 'dashboard', reportName: 'report'});

  const node = shallow(
    <NavItem
      name="testName"
      active={['/report/*', '/dashboard/*']}
      location={{pathname: '/dashboard/did/report/rid'}}
      breadcrumbsEntities={['dashboard', 'report']}
    />
  );

  await node.update();
  expect(loadEntitiesNames).toHaveBeenCalledWith({dashboardId: 'did', reportId: 'rid'});

  expect(node).toMatchSnapshot();
});

it('should update breadcrumbs when requested', async () => {
  const node = shallow(
    <NavItem
      name="testName"
      active={['/report/*', '/dashboard/*']}
      location={{pathname: '/dashboard/did/report/rid'}}
      breadcrumbsEntities={['dashboard', 'report']}
    />
  );

  await node.update();
  loadEntitiesNames.mockClear();

  expect(loadEntitiesNames).not.toHaveBeenCalled();
  refreshBreadcrumbs();
  expect(loadEntitiesNames).toHaveBeenCalled();
});
