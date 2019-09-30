/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {ConfirmationModal, Dropdown} from 'components';

import {editUser, removeUser} from './service';
import EditUserModal from './EditUserModal';

import UserListWithErrorHandling from './UserList';

jest.mock('./service', () => ({editUser: jest.fn(), removeUser: jest.fn()}));

const UserList = UserListWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  collection: 'collectionId',
  onChange: jest.fn(),
  readOnly: false,
  data: [
    {
      id: 'USER:kermit',
      identity: {
        id: 'kermit',
        type: 'user' // or group
      },
      role: 'manager' // or editor, viewer
    },
    {
      id: 'GROUP:sales',
      identity: {
        id: 'sales',
        type: 'group'
      },
      role: 'viewer'
    }
  ]
};

it('should match snapshot', () => {
  const node = shallow(<UserList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should hide add button and edit menu when in readOnly mode', () => {
  const node = shallow(<UserList {...props} readOnly />);

  expect(node).toMatchSnapshot();
});

it('should show delete modal when clicking delete button', () => {
  const node = shallow(<UserList {...props} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

  expect(node.find(ConfirmationModal).prop('open')).toBeTruthy();
});

it('should delete collection', () => {
  const node = shallow(<UserList {...props} />);

  node.setState({deleting: props.data[0]});
  node.find(ConfirmationModal).prop('onConfirm')();

  expect(removeUser).toHaveBeenCalledWith('collectionId', 'USER:kermit');
});

it('should show an edit modal when clicking the edit button', () => {
  const node = shallow(<UserList {...props} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(node.find(EditUserModal)).toExist();
});

it('should modify the user role', () => {
  const node = shallow(<UserList {...props} />);

  node.setState({editing: props.data[0]});
  node.find(EditUserModal).prop('onConfirm')('viewer');

  expect(editUser).toHaveBeenCalledWith('collectionId', 'USER:kermit', 'viewer');
});
