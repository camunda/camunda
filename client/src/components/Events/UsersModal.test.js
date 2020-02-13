/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import UsersModalWithErrorHandling from './UsersModal';

import {updateUsers, getUser} from './service';

const UsersModal = UsersModalWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  updateUsers: jest.fn(),
  getUser: jest.fn().mockReturnValue({id: 'USER:test', type: 'user', name: 'Test'}),
  getUsers: jest.fn().mockReturnValue([
    {
      id: 'USER:kermit',
      identity: {
        id: 'kermit',
        type: 'user' // or group
      }
    }
  ])
}));

const props = {
  id: 'processId',
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should add user/group to the list', () => {
  const node = shallow(<UsersModal {...props} />);

  expect(node.find('.confirm').props('disabled')).toBeTruthy();

  node.find('UserTypeahead').prop('onChange')({
    id: 'sales',
    type: 'group',
    name: 'Sales',
    memberCount: '2'
  });

  node.find('.confirm').simulate('click');

  expect(node.find('EntityList').props('data').data).toMatchSnapshot();
});

it('should disable the save button if the user list is empty', () => {
  const node = shallow(<UsersModal {...props} />);

  node
    .find('EntityList')
    .props('data')
    .data[0].meta3.props.onClick();

  expect(node.find('EntityList').props('data').data.length).toBe(0);
  expect(node.find({variant: 'primary'})).toBeDisabled();
});

it('should show an error when adding already existing user/group', () => {
  const node = shallow(<UsersModal {...props} />);
  node.find('UserTypeahead').prop('onChange')({
    id: 'kermit',
    name: 'Kermit',
    type: 'user'
  });
  node.find('.confirm').simulate('click');

  expect(node.find({error: true})).toExist();
});

it('should update the event with the selected users', () => {
  const node = shallow(<UsersModal {...props} />);
  node.find({variant: 'primary'}).simulate('click');

  expect(updateUsers).toHaveBeenCalled();
  expect(props.onClose).toHaveBeenCalledWith([
    {id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}}
  ]);
});

it('should load non imported user before adding it to the list', () => {
  const node = shallow(<UsersModal {...props} />);
  node.find('UserTypeahead').prop('onChange')({
    id: 'test'
  });
  node.find('.confirm').simulate('click');
  expect(getUser).toHaveBeenCalledWith('test');
  expect(node.find('EntityList').props('data').data[1].name).toBe('Test');
});
