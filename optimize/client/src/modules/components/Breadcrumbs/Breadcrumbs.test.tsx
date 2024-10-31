/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {Link, useLocation} from 'react-router-dom';
import {shallow} from 'enzyme';
import {BreadcrumbItem, BreadcrumbSkeleton} from '@carbon/react';

import Breadcrumbs from './Breadcrumbs';
import {getEntityId, loadEntitiesNames} from './service';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn(),
}));

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

jest.mock('./service', () => ({
  loadEntitiesNames: jest.fn(),
  getEntityId: jest.fn(),
}));

const mockUseLocation = useLocation as jest.Mock;
const mockLoadEntitiesNames = loadEntitiesNames as jest.Mock;
const mockGetEntityId = getEntityId as jest.Mock;

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render BreadcrumbSkeleton while entityNames is not set', () => {
  mockUseLocation.mockReturnValue({pathname: '/collection/123'});
  mockGetEntityId.mockImplementation((type) => (type === 'collection' ? '123' : null));

  const node = shallow(<Breadcrumbs />);
  runLastEffect();

  expect(node.find(BreadcrumbSkeleton).exists()).toBe(true);
});

it('should render homePageBreadcrumb with "collections" link if not instant dashboard or process overview', async () => {
  mockUseLocation.mockReturnValue({pathname: '/collection/123'});
  mockGetEntityId.mockImplementation((type) => (type === 'collection' ? '123' : null));
  const entityNames = {collectionName: 'Collection 123', dashboardName: null, reportName: null};
  mockLoadEntitiesNames.mockResolvedValue(entityNames);

  const node = shallow(<Breadcrumbs />);
  await runLastEffect();
  await flushPromises();

  expect(node.find(Link).at(0).prop('to')).toEqual('/collections');
});

it('should render homePageBreadcrumb with "dashboards" link for process overview route', () => {
  mockUseLocation.mockReturnValue({pathname: '/processes/123'});
  mockGetEntityId.mockImplementation(() => null);

  const node = shallow(<Breadcrumbs />);
  runLastEffect();

  expect(node.find(Link).at(0).prop('to')).toEqual('/');
});

it('should render a collection breadcrumb link when collection is defined', () => {
  const entityNames = {collectionName: 'Collection 123', dashboardName: null, reportName: null};
  mockUseLocation.mockReturnValue({pathname: '/collection/123'});
  mockGetEntityId.mockImplementation((type) => (type === 'collection' ? '123' : null));
  mockLoadEntitiesNames.mockReturnValue(entityNames);

  const node = shallow(<Breadcrumbs />);
  runLastEffect();

  expect(node.find(BreadcrumbItem).at(1).find(Link).prop('to')).toEqual('/collection/123/');
  expect(node.find(BreadcrumbItem).at(1)).toIncludeText(entityNames.collectionName);
});

it('should render dashboard breadcrumb link when report, dashboard and collection are defined', () => {
  const entityNames = {
    collectionName: 'Collection 123',
    dashboardName: 'Dashboard 456',
    reportName: null,
  };

  mockLoadEntitiesNames.mockReturnValue(entityNames);
  mockUseLocation.mockReturnValue({pathname: '/collection/123/dashboard/456/report/350'});
  mockGetEntityId.mockImplementation((type) => {
    if (type === 'collection') {
      return '123';
    }
    if (type === 'dashboard') {
      return '456';
    }
    if (type === 'report') {
      return '356';
    }
    return null;
  });

  const node = shallow(<Breadcrumbs />);
  runLastEffect();

  expect(node.find(BreadcrumbItem).at(2).find(Link).prop('to')).toEqual(
    '/collection/123/dashboard/456/'
  );
});

it('should not render a dashboard breadcrumb if dashboard is "instant"', () => {
  mockUseLocation.mockReturnValue({pathname: '/dashboard/instant'});
  mockGetEntityId.mockImplementation((type) => (type === 'dashboard' ? 'instant' : null));

  const node = shallow(<Breadcrumbs />);
  runLastEffect();

  expect(node.find(BreadcrumbItem).length).toBe(1);
  expect(node.find(BreadcrumbItem).find(Link).prop('to')).toEqual('/');
});
