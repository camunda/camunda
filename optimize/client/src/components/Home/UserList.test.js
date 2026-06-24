/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter, EntityList} from 'components';

import {editUser, removeUser} from './service';
import AddUserModal from './modals/AddUserModal';
import EditUserModal from './modals/EditUserModal';

import UserListWithErrorHandling from './UserList';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('ccsm'),
}));

jest.mock('./service', () => ({
  getUsers: jest.fn().mockReturnValue([
    {
      id: 'USER:kermit',
      identity: {
        id: 'kermit',
      },
      role: 'manager', // or editor, viewer
    },
    {
      id: 'USER:john',
      identity: {
        id: 'john',
      },
      role: 'editor',
    },
  ]),
  editUser: jest.fn(),
  removeUser: jest.fn(),
}));

const UserList = UserListWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  collection: 'collectionId',
  readOnly: false,
  onChange: jest.fn(),
};

it('should match snapshot', async () => {
  const node = await shallow(<UserList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should hide add button and edit menu when in readOnly mode', () => {
  const node = shallow(<UserList {...props} readOnly />);

  expect(node.find(EntityList).prop('action')).toBe(false);

  expect(node.find(EntityList).prop('rows')[0].actions).toBe(false);
});

it('should pass Entity to Deleter', () => {
  const node = shallow(<UserList {...props} />);

  node.find(EntityList).prop('rows')[1].actions[1].action();

  expect(node.find(Deleter).prop('entity').id).toBe('john');
});

it('should delete collection', () => {
  const node = shallow(<UserList {...props} />);

  node.setState({deleting: {id: 'USER:kermit', identity: {id: 'kermit'}}});
  node.find(Deleter).prop('deleteEntity')();

  expect(removeUser).toHaveBeenCalledWith('collectionId', 'USER:kermit');
});

it('should show an edit modal when clicking the edit button', () => {
  const node = shallow(<UserList {...props} />);

  node.find(EntityList).prop('rows')[1].actions[0].action();

  expect(node.find(EditUserModal)).toExist();
});

it('should modify the user role', () => {
  const node = shallow(<UserList {...props} />);

  node.setState({editing: {id: 'USER:kermit', identity: {id: 'kermit'}}});
  node.find(EditUserModal).prop('onConfirm')('viewer');

  expect(editUser).toHaveBeenCalledWith('collectionId', 'USER:kermit', 'viewer');
  expect(props.onChange).toHaveBeenCalled();
});

it('should pass optimize environment to addUserModal', async () => {
  const node = await shallow(<UserList {...props} />);

  node.find(EntityList).prop('action').props.onClick({});

  expect(node.find(AddUserModal).prop('optimizeProfile')).toBe('ccsm');
});
