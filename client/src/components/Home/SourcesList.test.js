/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityList, Deleter, Modal} from 'components';

import {getSources, removeSource, editSource, addSources} from './service';

import SourcesListWithErrorHandling from './SourcesList';
import EditSourceModal from './modals/EditSourceModal';
import SourcesModal from './modals/SourcesModal';
import {areTenantsAvailable} from 'config';

jest.mock('notifications', () => ({addNotification: jest.fn()}));
jest.mock('config', () => ({areTenantsAvailable: jest.fn().mockReturnValue(true)}));

jest.mock('./service', () => ({
  getSources: jest.fn().mockReturnValue([
    {
      id: 'process:defKey',
      definitionType: 'process',
      definitionKey: 'defKey',
      definitionName: 'definition name',
      tenants: [
        {id: null, name: 'Not defined'},
        {id: 'tenant1', name: 'Sales'},
        {id: '__unauthorizedTenantId__', name: 'Unauthorized Tenant'},
      ],
    },
    {
      id: 'decision:defKey2',
      definitionType: 'decision',
      definitionKey: 'defKey2',
      definitionName: 'decision report',
      tenants: [
        {id: null, name: 'Not defined'},
        {id: 'tenant1', name: 'Marketing'},
      ],
    },
  ]),
  removeSource: jest.fn(),
  editSource: jest.fn(),
  addSources: jest.fn(),
}));

const SourcesList = SourcesListWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  collection: 'collectionId',
  onChange: jest.fn(),
  readOnly: false,
};

it('should match snapshot', async () => {
  const node = shallow(<SourcesList {...props} />);
  await node.update();

  expect(node).toMatchSnapshot();
});

it('should hide add button and edit menu when in readOnly mode', async () => {
  const node = shallow(<SourcesList {...props} readOnly />);
  await node.update();

  expect(node.find(EntityList).prop('action')()).toBe(false);

  expect(node.find(EntityList).prop('data')[0].actions).toEqual([]);
});

it('should pass entity to Deleter', async () => {
  const node = shallow(<SourcesList {...props} />);
  await node.update();

  node.find(EntityList).prop('data')[1].actions[1].action();

  expect(node.find(Deleter).prop('entity').id).toBe('decision:defKey2');
});

it('should delete source', () => {
  const node = shallow(<SourcesList {...props} />);

  node.setState({deleting: {id: 'sourceId'}});

  node.find(Deleter).prop('deleteEntity')();

  expect(removeSource).toHaveBeenCalledWith('collectionId', 'sourceId');
});

it('should show an edit modal when clicking the edit button', async () => {
  const node = shallow(<SourcesList {...props} />);
  await node.update();

  node.find(EntityList).prop('data')[0].actions[0].action();

  expect(node.find(EditSourceModal)).toExist();
});

it('should modify the tenants in source', () => {
  const node = shallow(<SourcesList {...props} />);

  node.setState({editing: {id: 'sourceId'}});
  const updatedTenants = [{id: 'newTenant', name: 'New Tenant'}];
  node.find(EditSourceModal).prop('onConfirm')(updatedTenants);

  expect(editSource).toHaveBeenCalledWith('collectionId', 'sourceId', updatedTenants, undefined);
});

it('should add sources when SourcesModal is confirmed', () => {
  const node = shallow(<SourcesList {...props} />);

  node.setState({addingSource: true});
  const sources = [
    {
      id: 'sourceId',
      definitionType: 'process',
      definitionKey: 'defKey',
      tenants: ['tenantId'],
    },
  ];
  node.find(SourcesModal).prop('onConfirm')(sources);

  expect(addSources).toHaveBeenCalledWith('collectionId', sources);
});

it('should hide edit and tenants in source items if there are not tenants available', async () => {
  areTenantsAvailable.mockReturnValueOnce(false);
  const node = shallow(<SourcesList {...props} />);
  await node.update();
  expect(node.find('EntityList').props().data[1]).toMatchSnapshot();
});

it('should pass conflict to confirmation modal if update failed', async () => {
  const conflictedItems = [{id: 'reportId', type: 'report', name: 'Report Name'}];
  const mightFail = (promise, cb, err) => {
    if (err) {
      err({status: 409, conflictedItems});
    }
  };

  const node = shallow(<SourcesList {...props} mightFail={mightFail} />);

  node.setState({editing: {id: 'sourceId'}});
  const updatedTenants = [{id: 'newTenant', name: 'New Tenant'}];
  node.find(EditSourceModal).prop('onConfirm')(updatedTenants);

  await node.update();
  await flushPromises();

  expect(node.find(Modal.Content)).toIncludeText(conflictedItems[0].name);

  node.setProps({mightFail: props.mightFail});
  node.find('.confirm').simulate('click');

  expect(editSource).toHaveBeenCalledWith('collectionId', 'sourceId', updatedTenants, true);
  expect(getSources).toHaveBeenCalled();
  expect(props.onChange).toHaveBeenCalled();
});
