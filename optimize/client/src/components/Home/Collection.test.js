/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Deleter} from 'components';
import {loadEntity, updateEntity} from 'services';
import {isUserSearchAvailable} from 'config';

import {Collection} from './Collection';
import Copier from './Copier';
import CollectionModal from './modals/CollectionModal';
import {loadCollectionEntities} from './service';
import UserList from './UserList';
import {C3Page} from '@camunda/camunda-composite-components';

jest.mock('config', () => ({
  isUserSearchAvailable: jest.fn().mockReturnValue(true),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    deleteEntity: jest.fn(),
    updateEntity: jest.fn(),
    loadEntity: jest.fn().mockReturnValue({
      id: 'aCollectionId',
      name: 'aCollectionName',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      created: '2017-11-11T11:11:11.1111+0200',
      owner: 'user_id',
      lastModifier: 'user_id',
      currentUserRole: 'manager', // or editor, viewer
      data: {
        configuration: {},
        roles: [
          {
            identity: {
              id: 'kermit',
              type: 'user',
            },
            role: 'manager', // or editor, viewer
          },
        ], // array of role objects, for details see role endpoints
        scope: [], // array of scope objects, for details see scope endpoints
      },
    }),
  };
});

jest.mock('./service', () => ({
  loadCollectionEntities: jest.fn().mockReturnValue([
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      description: 'a description',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      created: '2017-11-11T11:11:11.1111+0200',
      owner: 'user_id',
      lastModifier: 'user_id',
      entityType: 'dashboard',
      currentUserRole: 'editor', // or viewer
      data: {
        subEntityCounts: {
          report: 8,
        },
        roleCounts: {},
      },
    },
    {
      id: 'aReportId',
      name: 'aReport',
      lastModified: '2017-11-11T11:11:11.1111+0200',
      created: '2017-11-11T11:11:11.1111+0200',
      owner: 'user_id',
      lastModifier: 'user_id',
      reportType: 'process',
      entityType: 'report',
      data: {
        subEntityCounts: {},
        roleCounts: {},
      },
      currentUserRole: 'editor', // or viewer
    },
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb, _err, final) => {
    cb(data);
    final?.();
  }),
  match: {params: {id: 'aCollectionId'}},
};

beforeEach(() => {
  loadCollectionEntities.mockClear();
});

it('should pass Entity to Deleter', () => {
  const node = shallow(<Collection {...props} />);

  node.find('CollectionEnitiesList').prop('deleteEntity')({
    id: 'aDashboardId',
    name: 'aDashboard',
    description: 'a description',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    entityType: 'dashboard',
    currentUserRole: 'editor', // or viewer
    data: {
      subEntityCounts: {
        report: 8,
      },
      roleCounts: {},
    },
  });

  expect(node.find(Deleter).prop('entity').id).toBe('aDashboardId');
});

it('should modify the collections name with the edit modal', async () => {
  const node = shallow(<Collection {...props} />);

  node.setState({editingCollection: true});
  await node.find(CollectionModal).prop('onConfirm')('new Name');

  expect(updateEntity).toHaveBeenCalledWith('collection', 'aCollectionId', {name: 'new Name'});
});

it('should hide edit/delete from context menu for collection items that does not have a "manager" role', () => {
  loadEntity.mockReturnValueOnce({
    id: 'aCollectionId',
    name: 'aCollectionName',
    owner: 'user_id',
    lastModifier: 'user_id',
    currentUserRole: 'editor',
    data: {},
  });
  const node = shallow(<Collection {...props} />);

  expect(node.find('.name').find({type: 'delete'})).not.toExist();
  expect(node.find('.name').find({type: 'edit'})).not.toExist();
});

it('should render content depending on the selected tab', async () => {
  const node = await shallow(
    <Collection {...props} match={{params: {id: 'aCollectionId', viewMode: 'users'}}} />
  );

  await flushPromises();

  expect(node.find('Tabs').prop('value')).toBe('users');
  expect(node.find(UserList).prop('collection')).toBe('aCollectionId');
});

it('should show the copy modal when clicking the copy button', () => {
  const node = shallow(<Collection {...props} />);

  node
    .find(C3Page)
    .prop('header')
    .menuItems.find(({key}) => key === 'copy')
    .onClick();

  expect(node.find(Copier)).toExist();
});

it('should load collection entities with sort parameters', () => {
  const node = shallow(<Collection {...props} />);

  node.find('CollectionEnitiesList').prop('loadEntities')('lastModifier', 'desc');

  expect(loadCollectionEntities).toHaveBeenCalledWith('aCollectionId', 'lastModifier', 'desc');
});

it('should set the loading state of the entity list', async () => {
  const node = shallow(
    <Collection
      {...props}
      mightFail={async (data, cb, _err, final) => {
        cb(await data);
        final?.();
      }}
    />
  );

  runAllEffects();

  expect(node.find('CollectionEnitiesList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('CollectionEnitiesList').prop('isLoading')).toBe(false);

  node.find('CollectionEnitiesList').prop('loadEntities')('lastModifier', 'desc');

  expect(node.find('CollectionEnitiesList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('CollectionEnitiesList').prop('isLoading')).toBe(false);
});

it('should hide alerts tab if user search is not available', async () => {
  isUserSearchAvailable.mockReturnValueOnce(false);
  const node = await shallow(<Collection {...props} />);

  await flushPromises();

  expect(node.find({title: 'Alerts'})).not.toExist();
});

it('should hide users tab if user search is not available', async () => {
  isUserSearchAvailable.mockReturnValueOnce(false);
  const node = await shallow(<Collection {...props} />);

  await flushPromises();

  expect(node.find({title: 'Users'})).not.toExist();
});
