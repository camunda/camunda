/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {showError} from 'notifications';

import {UsersModal} from './UsersModal';
import {updateUsers, getUser, getUsers} from './service';

jest.mock('./service', () => ({
  updateUsers: jest.fn(),
  getUser: jest.fn().mockReturnValue({id: 'USER:test', type: 'user', name: 'Test'}),
  getUsers: jest.fn().mockReturnValue([
    {
      id: 'USER:kermit',
      identity: {
        id: 'kermit',
        type: 'user', // or group
      },
    },
  ]),
}));

jest.mock('notifications', () => ({showError: jest.fn()}));

beforeEach(() => updateUsers.mockClear());

const props = {
  id: 'processId',
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should add user/group to the list', () => {
  const node = shallow(<UsersModal {...props} />);

  node.find('MultiUserTypeahead').prop('onAdd')({
    id: 'sales',
    type: 'group',
    name: 'Sales',
    memberCount: '2',
  });

  node.find('[primary]').simulate('click');

  expect(updateUsers).toHaveBeenCalledWith('processId', [
    {id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}},
    {id: 'GROUP:sales', identity: {id: 'sales', memberCount: '2', name: 'Sales', type: 'group'}},
  ]);
});

it('should disable the save button if the user list is empty', () => {
  getUsers.mockReturnValueOnce([]);
  const node = shallow(<UsersModal {...props} />);

  expect(node.find('MultiUserTypeahead').prop('users').length).toBe(0);
  expect(node.find('[primary]')).toBeDisabled();
});

it('should show an error when adding already existing user/group', () => {
  const node = shallow(<UsersModal {...props} />);

  node.find('MultiUserTypeahead').prop('onAdd')({
    id: 'kermit',
    name: 'Kermit',
    type: 'user',
  });

  expect(showError).toHaveBeenCalled();
});

it('should load non imported user before adding it to the list', () => {
  getUsers.mockReturnValueOnce([]);
  const node = shallow(<UsersModal {...props} />);
  node.find('MultiUserTypeahead').prop('onAdd')({
    id: 'test',
  });
  expect(getUser).toHaveBeenCalledWith('test');
  node.find('[primary]').simulate('click');
  expect(updateUsers).toHaveBeenCalledWith('processId', [
    {
      id: 'USER:USER:test',
      identity: {id: 'USER:test', memberCount: undefined, name: 'Test', type: 'user'},
    },
  ]);
});
