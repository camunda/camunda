/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityList, Deleter} from 'components';

import {editUser, removeUser} from './service';
import EditUserModal from './modals/EditUserModal';

import UserListWithErrorHandling from './UserList';

jest.mock('./service', () => ({
  getUsers: jest.fn().mockReturnValue([
    {
      id: 'USER:kermit',
      identity: {
        id: 'kermit',
        type: 'user' // or group
      },
      role: 'manager', // or editor, viewer
      hasFullScopeAuthorizations: false
    },
    {
      id: 'GROUP:sales',
      identity: {
        id: 'sales',
        type: 'group',
        memberCount: 2
      },
      role: 'manager'
    }
  ]),
  editUser: jest.fn(),
  removeUser: jest.fn()
}));

const UserList = UserListWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  collection: 'collectionId',
  readOnly: false,
  onChange: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<UserList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should hide add button and edit menu when in readOnly mode', () => {
  const node = shallow(<UserList {...props} readOnly />);

  expect(node).toMatchSnapshot();
});

it('should pass Entity to Deleter', () => {
  const node = shallow(<UserList {...props} />);

  node
    .find(EntityList)
    .prop('data')[0]
    .actions[1].action();

  expect(node.find(Deleter).prop('entity').id).toBe('kermit');
});

it('should delete collection', () => {
  const node = shallow(<UserList {...props} />);

  node.setState({deleting: {id: 'USER:kermit', identity: {id: 'kermit'}}});
  node.find(Deleter).prop('deleteEntity')();

  expect(removeUser).toHaveBeenCalledWith('collectionId', 'USER:kermit');
});

it('should show an edit modal when clicking the edit button', () => {
  const node = shallow(<UserList {...props} />);

  node
    .find(EntityList)
    .prop('data')[0]
    .actions[0].action();

  expect(node.find(EditUserModal)).toExist();
});

it('should modify the user role', () => {
  const node = shallow(<UserList {...props} />);

  node.setState({editing: {id: 'USER:kermit', identity: {id: 'kermit'}}});
  node.find(EditUserModal).prop('onConfirm')('viewer');

  expect(editUser).toHaveBeenCalledWith('collectionId', 'USER:kermit', 'viewer');
  expect(props.onChange).toHaveBeenCalled();
});
