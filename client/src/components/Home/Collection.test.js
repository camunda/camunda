/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown, EntityList, Deleter} from 'components';
import {refreshBreadcrumbs} from 'components/navigation';
import {loadEntity, updateEntity} from 'services';

import CollectionWithErrorHandling from './Collection';
import Copier from './Copier';
import CollectionModal from './modals/CollectionModal';

jest.mock('./service', () => ({copyEntity: jest.fn()}));

const Collection = CollectionWithErrorHandling.WrappedComponent;

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
        entities: [
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
                report: 8
              },
              roleCounts: {}
            }
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
              roleCounts: {}
            },
            currentUserRole: 'editor' // or viewer
          }
        ],
        roles: [
          {
            identity: {
              id: 'kermit',
              type: 'user' // or group
            },
            role: 'manager' // or editor, viewer
          }
        ], // array of role objects, for details see role endpoints
        scope: [] // array of scope objects, for details see scope endpoints
      }
    })
  };
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  match: {params: {id: 'aCollectionId'}}
};

it('should pass Entity to Deleter', () => {
  const node = shallow(<Collection {...props} />);

  node
    .find(EntityList)
    .prop('data')[0]
    .actions[2].action();

  expect(node.find(Deleter).prop('entity').id).toBe('aDashboardId');
});

it('should show an edit modal when clicking the edit button', () => {
  const node = shallow(<Collection {...props} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(node.find(CollectionModal)).toExist();
});

it('should modify the collections name with the edit modal', async () => {
  const node = shallow(<Collection {...props} />);

  node.setState({editingCollection: true});
  await node.find(CollectionModal).prop('onConfirm')('new Name');

  expect(updateEntity).toHaveBeenCalledWith('collection', 'aCollectionId', {name: 'new Name'});
  expect(refreshBreadcrumbs).toHaveBeenCalled();
});

it('should hide edit/delete from context menu for collection items that does not have a "manager" role', () => {
  loadEntity.mockReturnValueOnce({
    id: 'aCollectionId',
    name: 'aCollectionName',
    owner: 'user_id',
    lastModifier: 'user_id',
    currentUserRole: 'editor',
    data: {
      entities: []
    }
  });
  const node = shallow(<Collection {...props} />);

  expect(node.find('.name').find({type: 'delete'})).not.toExist();
  expect(node.find('.name').find({type: 'edit'})).not.toExist();
});

it('should render content depending on the selected tab', () => {
  const node = shallow(
    <Collection {...props} match={{params: {id: 'aCollectionId', viewMode: 'users'}}} />
  );

  expect(node).toMatchSnapshot();
});

it('should show the copy modal when clicking the copy button', () => {
  const node = shallow(<Collection {...props} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

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
    data: {
      entities: []
    }
  });
  const node = shallow(<Collection {...props} />);

  expect(node.find('EntityList').prop('action')).toBe(false);
});
