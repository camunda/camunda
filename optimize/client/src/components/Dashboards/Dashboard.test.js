/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {loadEntity, createEntity} from 'services';
import {showError} from 'notifications';

import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';
import {Dashboard} from './Dashboard';
import {isAuthorizedToShareDashboard} from './service';

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('./service', () => {
  return {
    isAuthorizedToShareDashboard: jest.fn(),
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadEntity: jest.fn().mockReturnValue({}),
    deleteEntity: jest.fn(),
    createEntity: jest.fn().mockReturnValue('id'),
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {},
  mightFail: (promise, cb) => cb(promise),
  getUser: () => ({id: 'demo', name: 'Demo Demo'}),
  entity: 'dashboard',
  history: {replace: jest.fn(), push: jest.fn()},
};

const templateState = {
  name: 'My Dashboard',
  definitionKey: 'aKey',
  versions: ['1'],
  tenants: [null],
  definitionName: 'A Process Definition',
  xml: 'xml string',
  data: [
    {
      position: {x: 0, y: 0},
      dimensions: {height: 2, width: 4},
      report: {
        name: 'A Report Name',
        data: {view: {entity: 'processInstance', properties: ['frequency']}},
      },
      type: 'optimize_report',
    },
    {
      position: {
        x: 2,
        y: 0,
      },
      dimensions: {
        width: 2,
        height: 4,
      },
      type: 'text',
      configuration: {
        text: {
          root: {
            children: [],
            indent: 0,
            format: '',
            type: 'root',
            version: 1,
            direction: 'ltr',
          },
        },
      },
    },
  ],
};

beforeEach(() => {
  props.match.params.viewMode = 'view';
  jest.clearAllMocks();
});

afterEach(async () => {
  await flushPromises();
});

it("should show an error page if dashboard doesn't exist", () => {
  const node = shallow(<Dashboard {...props} />);

  node.setState({
    serverError: 404,
  });

  expect(node).toIncludeText('ErrorPage');
});

it('should show a notification error if dashboard fails to load on refresh', () => {
  const node = shallow(<Dashboard {...props} />);

  node.setState({
    loaded: true,
  });

  node.setProps({mightFail: (promise, cb, error) => error('Loading failed')});

  node.instance().loadDashboard();

  expect(showError).toHaveBeenCalled();
  expect(node.find('.Dashboard')).toExist();
});

it('should display a loading indicator', () => {
  const node = shallow(<Dashboard {...props} />);

  expect(node.find('Loading')).toExist();
});

it('should initially load data', async () => {
  shallow(<Dashboard {...props} />);

  await flushPromises();

  expect(loadEntity).toHaveBeenCalledWith('dashboard', '1', undefined);
});

it('should not load data when it is a new dashboard', () => {
  shallow(<Dashboard {...props} match={{params: {id: 'new'}}} />);

  expect(loadEntity).not.toHaveBeenCalledWith('dashboard', 'new');
});

it('should create a new dashboard when saving a new one', async () => {
  const node = shallow(<Dashboard {...props} match={{params: {id: 'new'}}} />);

  await node
    .instance()
    .saveChanges('testname', 'description', [{id: 'reportID'}], [{type: 'state'}]);

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    collectionId: null,
    name: 'testname',
    description: 'description',
    tiles: [{id: 'reportID'}],
    availableFilters: [{type: 'state'}],
  });
});

it('should create a new dashboard in a collection', async () => {
  const node = shallow(
    <Dashboard
      {...props}
      location={{pathname: '/collection/123/dashboard/new/edit'}}
      match={{params: {id: 'new'}}}
    />
  );

  await node.instance().saveChanges('testname', 'description', [], []);

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    collectionId: '123',
    name: 'testname',
    description: 'description',
    tiles: [],
    availableFilters: [],
  });
});

it('should redirect to the Overview page on dashboard deletion', async () => {
  const node = shallow(<Dashboard {...props} />);
  // the componentDidUpdate is mocked because it resets the redirect state
  // which prevents the redirect component from rendering while testing
  node.instance().componentDidUpdate = jest.fn();

  node.setState({
    loaded: true,
  });

  await node.find(DashboardView).prop('onDelete')();

  expect(props.history.push).toHaveBeenCalledWith('../../');
});

it('should initialize tiles based on location state', async () => {
  const node = shallow(
    <Dashboard
      {...props}
      match={{params: {id: 'new'}}}
      location={{
        state: templateState,
      }}
    />
  );

  await flushPromises();

  const tiles = node.find(DashboardView).prop('tiles');

  expect(tiles.length).toBe(2);
  expect(tiles[0].report.name).toBe('A Report Name');
  expect(tiles[0].report.data.view.entity).toBe('processInstance');
  expect(tiles[1].type).toBe('text');
});

it('should save unsaved tiles when saving dashboard', async () => {
  const node = shallow(
    <Dashboard
      {...props}
      match={{params: {id: 'new', viewMode: 'edit'}}}
      location={{
        state: templateState,
      }}
    />
  );

  await flushPromises();

  node.find(DashboardEdit).prop('saveChanges')(
    'new Dashboard Name',
    'description',
    node.find(DashboardEdit).prop('initialTiles'),
    []
  );

  expect(createEntity.mock.calls.length).toBe(1);

  const firstSave = createEntity.mock.calls[0];
  expect(firstSave[0]).toBe('report/process/single');
  expect(firstSave[1].data).toEqual(node.find(DashboardEdit).prop('initialTiles')[0].report.data);
});

it('should display DashboardView component when displaying instant dashboard', () => {
  const node = shallow(
    <Dashboard
      {...props}
      entity="dashboard/instant"
      match={{params: {id: '1', viewMode: 'edit'}}}
    />
  );

  expect(node.find('DashbaordView')).toBeDefined();
});

it('should call loadEntity with template param', async () => {
  shallow(
    <Dashboard
      {...props}
      entity="dashboard/instant"
      location={{search: '?template=template.json'}}
    />
  );

  await flushPromises();

  expect(loadEntity).toHaveBeenCalledWith('dashboard/instant', '1', {template: 'template.json'});
});

it('should redirect to instant preview dashboard from magic link', async () => {
  shallow(
    <Dashboard
      {...props}
      entity="dashboard"
      location={{pathname: '/collection/id/dashboard/id/'}}
      match={{params: {id: 'id'}}}
    />
  );

  await flushPromises();

  expect(props.history.replace).toHaveBeenCalledWith('/dashboard/instant/id');
});

it('should hide sharing for instant preview dashboard', async () => {
  loadEntity.mockReturnValueOnce({instantPreviewDashboard: true});
  const node = shallow(
    <Dashboard {...props} entity="dashboard/instant" match={{params: {id: '1'}}} />
  );

  await flushPromises();

  expect(node.find(DashboardView).prop('isInstantDashboard')).toBe(true);
});

it('should invoke isAuthorizedToShareDashboard on mount', async () => {
  await shallow(
    <Dashboard
      {...props}
      entity="dashboard/instant"
      match={{params: {id: '1', viewMode: 'edit'}}}
    />
  );

  expect(isAuthorizedToShareDashboard).toHaveBeenCalled();
});

it('should not invoke isAuthorizedToShareDashboard for instant preview dashboard', async () => {
  loadEntity.mockReturnValueOnce({instantPreviewDashboard: true});
  await shallow(<Dashboard {...props} entity="dashboard/instant" />);

  expect(isAuthorizedToShareDashboard).not.toHaveBeenCalled();
});
