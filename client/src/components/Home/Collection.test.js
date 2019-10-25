/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown, ConfirmationModal} from 'components';
import {refreshBreadcrumbs} from 'components/navigation';
import {loadEntity, deleteEntity, updateEntity} from 'services';

import CollectionWithErrorHandling from './Collection';
import CollectionModal from './CollectionModal';
import CopyModal from './CopyModal';
import {copyEntity} from './service';

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
              }
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

it('should match snapshot', () => {
  const node = shallow(<Collection {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show delete modal when clicking delete button', () => {
  const node = shallow(<Collection {...props} />);

  node
    .find(Dropdown.Option)
    .at(2)
    .simulate('click');

  expect(node.find(ConfirmationModal).prop('open')).toBe(true);
});

it('should delete collection', () => {
  const node = shallow(<Collection {...props} />);

  node.find(ConfirmationModal).prop('onConfirm')();

  expect(deleteEntity).toHaveBeenCalledWith('collection', 'aCollectionId');
  expect(node).toMatchSnapshot();
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

  expect(node.find(CopyModal)).toExist();
});

it('should copy entity and redirect to collection', () => {
  copyEntity.mockReturnValue('copyCollectionID');
  const node = shallow(<Collection {...props} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

  node.find(CopyModal).prop('onConfirm')('new Name', true);

  expect(copyEntity).toHaveBeenCalledWith('collection', 'aCollectionId', 'new Name');

  expect(node.find('Redirect').props().to).toBe('/collection/copyCollectionID/');

  expect(node.find(CopyModal)).not.toExist();
});
