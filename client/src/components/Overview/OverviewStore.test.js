/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {addNotification} from 'notifications';

import {StoreProvider} from './OverviewStore';
import {checkDeleteConflict, toggleEntityCollection, loadEntities, createEntity} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    createEntity: jest.fn(),
    loadEntities: jest.fn(),
    deleteEntity: jest.fn(),
    getEntitiesCollections: jest.fn().mockReturnValue({}),
    checkDeleteConflict: jest.fn().mockReturnValue([]),
    toggleEntityCollection: jest.fn().mockReturnValue(jest.fn())
  };
});

jest.mock('notifications', () => ({addNotification: jest.fn()}));

const OverviewStore = StoreProvider.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation(async (promise, cb) => cb(await promise))
};

const processReport = {
  id: 'reportID',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
};

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [processReport]
  }
};

beforeAll(() => {
  loadEntities.mockReturnValueOnce([collection]);
  loadEntities.mockReturnValueOnce([processReport]);
  loadEntities.mockReturnValueOnce([dashboard]);
});

it('should load data', () => {
  shallow(<OverviewStore {...props} />);

  expect(loadEntities).toHaveBeenCalledWith('report', 'lastModified');
  expect(loadEntities).toHaveBeenCalledWith('dashboard', 'lastModified');
  expect(loadEntities).toHaveBeenCalledWith('collection', 'created');
});

it('should reload the list after duplication', async () => {
  createEntity.mockReturnValueOnce('newReport');
  const node = shallow(<OverviewStore {...props} />);

  await node.instance().duplicateEntity('Report', processReport)({target: {blur: jest.fn()}});

  expect(loadEntities).toHaveBeenCalledWith('report', 'lastModified');
});

it('should add the entity to the collection that was duplicated from', async () => {
  createEntity.mockReturnValueOnce('newReport');
  const node = shallow(<OverviewStore {...props} />);

  await node.instance().duplicateEntity('report', processReport, collection)({
    target: {blur: jest.fn()}
  });

  expect(toggleEntityCollection()).toHaveBeenCalledWith({id: 'newReport'}, collection, false);
});

it('should check for deletion conflicts for reports and dashboards', async () => {
  checkDeleteConflict.mockClear();
  const node = shallow(<OverviewStore {...props} />);
  await node.instance().componentDidMount();

  await node.instance().showDeleteModalFor({type: 'entityType', entity: processReport})();

  expect(checkDeleteConflict).toHaveBeenCalledWith(processReport.id, 'entityType');
});

it('should reload the reports after deleting a report', async () => {
  loadEntities.mockClear();
  const node = shallow(<OverviewStore {...props} />);

  node.setState({
    deleting: {type: 'report', entity: processReport}
  });

  await node.instance().deleteEntity();

  expect(loadEntities).toHaveBeenCalledWith('report', 'lastModified');
});

it('should duplicate dashboards', () => {
  createEntity.mockClear();

  const node = shallow(<OverviewStore {...props} />);

  node.instance().duplicateEntity('dashboard', dashboard)({target: {blur: jest.fn()}});

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    ...dashboard,
    name: dashboard.name + ' - Copy'
  });
});

it('should display an error notification if request goes wrong', async () => {
  const node = shallow(
    <OverviewStore
      mightFail={(promise, cb, fail) =>
        fail({json: () => ({errorMessage: 'Something went wrong'})})
      }
    />
  );

  node.instance().setCollectionToUpdate({});
  await node.instance().updateOrCreateCollection('Collection');

  expect(addNotification).toHaveBeenCalledWith({type: 'error', text: 'Something went wrong'});
});

it('should handle different error formats', async () => {
  const node = shallow(
    <OverviewStore mightFail={(promise, cb, fail) => fail('Something went wrong')} />
  );

  addNotification.mockClear();
  node.instance().setCollectionToUpdate({});
  await node.instance().updateOrCreateCollection('Collection');

  expect(addNotification).toHaveBeenCalledWith({type: 'error', text: 'Something went wrong'});

  node.setProps({mightFail: (promise, cb, fail) => fail({message: 'Something went wrong'})});

  addNotification.mockClear();
  await node.instance().updateOrCreateCollection('Collection');
  expect(addNotification).toHaveBeenCalledWith({type: 'error', text: 'Something went wrong'});
});
