/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import DashboardWithErrorHandling from './Dashboard';
import {loadEntity, deleteEntity, createEntity} from 'services';

const {WrappedComponent: Dashboard} = DashboardWithErrorHandling;

jest.mock('./service', () => {
  return {
    isAuthorizedToShareDashboard: jest.fn()
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadEntity: jest.fn().mockReturnValue({}),
    deleteEntity: jest.fn(),
    createEntity: jest.fn().mockReturnValue('id')
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {},
  mightFail: (promise, cb) => cb(promise)
};

beforeEach(() => {
  props.match.params.viewMode = 'view';
});

it("should show an error page if dashboard doesn't exist", () => {
  const node = shallow(<Dashboard {...props} />);

  node.setState({
    serverError: 404
  });

  expect(node).toIncludeText('ErrorPage');
});

it('should display a loading indicator', () => {
  const node = shallow(<Dashboard {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should initially load data', async () => {
  await shallow(<Dashboard {...props} />);

  expect(loadEntity).toHaveBeenCalledWith('dashboard', '1');
});

it('should not load data when it is a new dashboard', async () => {
  await shallow(<Dashboard {...props} match={{params: {id: 'new'}}} />);

  expect(loadEntity).not.toHaveBeenCalledWith('dashboard', 'new');
});

it('should create a new dashboard when saving a new one', async () => {
  const node = await shallow(<Dashboard {...props} match={{params: {id: 'new'}}} />);

  node.instance().saveChanges('testname', [{id: 'reportID'}]);

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    collectionId: null,
    name: 'testname',
    reports: [{id: 'reportID'}]
  });
});

it('should create a new dashboard in a collection', async () => {
  const node = await shallow(
    <Dashboard
      {...props}
      location={{pathname: '/collection/123/dashboard/new/edit'}}
      match={{params: {id: 'new'}}}
    />
  );

  node.instance().saveChanges('testname', []);

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    collectionId: '123',
    name: 'testname',
    reports: []
  });
});

it('should remove a dashboard on dashboard deletion', () => {
  const node = shallow(<Dashboard {...props} />);

  node.instance().deleteDashboard();

  expect(deleteEntity).toHaveBeenCalledWith('dashboard', '1');
});

it('should redirect to the Overview page on dashboard deletion', async () => {
  const node = shallow(<Dashboard {...props} />);
  // the componentDidUpdate is mocked because it resets the redirect state
  // which prevents the redirect component from rendering while testing
  node.instance().componentDidUpdate = jest.fn();

  node.setState({
    loaded: true
  });

  await node.instance().deleteDashboard();

  expect(node.find('Redirect')).toExist();
});
