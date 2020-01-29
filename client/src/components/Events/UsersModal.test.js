/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import UsersModalWithErrorHandling from './UsersModal';

import {updateUsers} from './service';

const UsersModal = UsersModalWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  updateUsers: jest.fn(),
  getUsers: jest.fn().mockReturnValue([
    {
      id: 'user:kermit',
      isOwner: true,
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

it('should add/remove user/group from the list', () => {
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

  node
    .find('EntityList')
    .props('data')
    .data[1].meta3.props.onClick();

  expect(node.find('EntityList').props('data').data.length).toBe(1);
});

it('should show an error when adding already existing user/group', () => {
  const node = shallow(<UsersModal {...props} />);
  node.find('UserTypeahead').prop('onChange')({
    id: 'kermit',
    type: 'user'
  });
  node.find('.confirm').simulate('click');

  expect(node.find({error: true})).toExist();
});

it('should update the event with the selected users', () => {
  const node = shallow(<UsersModal {...props} />);
  node.find({variant: 'primary'}).simulate('click');

  expect(updateUsers).toHaveBeenCalled();
  expect(props.onClose).toHaveBeenCalledWith(true);
});
