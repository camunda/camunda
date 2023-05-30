/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Dropdown, EntityList, Deleter, ReportTemplateModal, Badge} from 'components';
import {refreshBreadcrumbs} from 'components/navigation';
import {loadEntity, updateEntity} from 'services';
import {getOptimizeProfile} from 'config';

import {Collection} from './Collection';
import Copier from './Copier';
import CollectionModal from './modals/CollectionModal';
import {loadCollectionEntities} from './service';
import UserList from './UserList';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

jest.mock('components/navigation', () => ({refreshBreadcrumbs: jest.fn()}));

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
              type: 'user', // or group
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
      reportType: 'process', // or "decision"
      combined: false,
      entityType: 'report',
      data: {
        subEntityCounts: {},
        roleCounts: {},
      },
      currentUserRole: 'editor', // or viewer
    },
  ]),
  copyEntity: jest.fn(),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb, err, final) => {
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

  node.find(EntityList).prop('data')[0].actions[2].action();

  expect(node.find(Deleter).prop('entity').id).toBe('aDashboardId');
});

it('should show an edit modal when clicking the edit button', () => {
  const node = shallow(<Collection {...props} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(node.find(CollectionModal)).toExist();
});

it('should modify the collections name with the edit modal', async () => {
  const node = shallow(<Collection {...props} />);

  node.setState({editingCollection: true});
  await node.find(CollectionModal).prop('onConfirm')('new Name');

  expect(updateEntity).toHaveBeenCalledWith('collection', 'aCollectionId', {name: 'new Name'});
  expect(refreshBreadcrumbs).toHaveBeenCalled();
});

it('should show a ReportTemplateModal', () => {
  const node = shallow(<Collection {...props} />);

  node.find('EntityList').prop('action')().props.createProcessReport();

  expect(node.find(ReportTemplateModal)).toExist();
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

  expect(node.find('.navigation .active').text()).toBe('Users');
  expect(node.find(UserList).prop('collection')).toBe('aCollectionId');
});

it('should show the copy modal when clicking the copy button', () => {
  const node = shallow(<Collection {...props} />);

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(node.find(Copier)).toExist();
});

it('should hide create new button if the user role is viewer', () => {
  loadEntity.mockReturnValue({
    id: 'aCollectionId',
    name: 'aCollectionName',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    currentUserRole: 'viewer',
    data: {},
  });
  const node = shallow(<Collection {...props} />);

  expect(node.find('EntityList').prop('action')()).toBe(false);
});

it('should load collection entities with sort parameters', () => {
  const node = shallow(<Collection {...props} />);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadCollectionEntities).toHaveBeenCalledWith('aCollectionId', 'lastModifier', 'desc');
});

it('should set the loading state of the entity list', async () => {
  const node = shallow(
    <Collection
      {...props}
      mightFail={async (data, cb, err, final) => {
        cb(await data);
        final?.();
      }}
    />
  );

  runAllEffects();

  expect(node.find('EntityList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('EntityList').prop('isLoading')).toBe(false);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(node.find('EntityList').prop('isLoading')).toBe(true);
  await flushPromises();
  expect(node.find('EntityList').prop('isLoading')).toBe(false);
});

it('should include an option to export reports for entity editors', () => {
  const node = shallow(<Collection {...props} />);

  expect(
    node
      .find('EntityList')
      .prop('data')[1]
      .actions.find(({text}) => text === 'Export')
  ).not.toBe(undefined);
});

it('should hide the export option for entity viewers', () => {
  loadCollectionEntities.mockReturnValueOnce([
    {
      entityType: 'report',
      currentUserRole: 'viewer',
      lastModified: '2019-11-18T12:29:37+0000',
      data: {subEntityCounts: {}},
    },
  ]);
  const node = shallow(<Collection {...props} />);

  expect(
    node
      .find('EntityList')
      .prop('data')[0]
      .actions.find(({text}) => text === 'Export')
  ).toBe(undefined);
});

it('should hide alerts and users tab in the ccsm environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('ccsm');
  const node = await shallow(<Collection {...props} />);

  expect(node.find({to: 'alerts'})).not.toExist();
  expect(node.find({to: 'users'})).not.toExist();
});

it('should display badge with user role Manager', () => {
  loadEntity.mockReturnValue({
    id: 'aCollectionId',
    name: 'aCollectionName',
    lastModified: '2017-11-11T11:11:11.1111+0200',
    created: '2017-11-11T11:11:11.1111+0200',
    owner: 'user_id',
    lastModifier: 'user_id',
    currentUserRole: 'manager',
    data: {},
  });

  const node = shallow(<Collection {...props} />);

  expect(node.find(Badge).children().text()).toBe('Manager');
});
