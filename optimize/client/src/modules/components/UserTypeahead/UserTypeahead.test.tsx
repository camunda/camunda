/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {DropdownSkeleton} from '@carbon/react';

import {showError} from 'notifications';

import UserTypeahead from './UserTypeahead';
import {getUser} from './service';

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  getUser: jest.fn().mockReturnValue({id: 'kermit', type: 'user', name: 'Kermit'}),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
}));

const testUser = {
  id: 'USER:kermit',
  identity: {
    name: 'kermit',
    id: 'kermit',
    email: 'kermit@kermit.com',
  },
};

it('should invoke onChange when adding a user', () => {
  const spy = jest.fn();
  const node = shallow(<UserTypeahead users={[testUser]} onChange={spy} />);

  node.find('MultiUserInput').simulate('add', {
    id: 'user2',
    name: 'User 2',
  });

  expect(spy).toHaveBeenCalledWith([
    testUser,
    {id: 'USER:user2', identity: {email: undefined, id: 'user2', name: 'User 2'}},
  ]);
});

it('should show an error when adding already existing user/group', () => {
  const node = shallow(<UserTypeahead users={[testUser]} onChange={jest.fn()} />);

  node.find('MultiUserInput').simulate('add', {id: 'kermit'});

  expect(showError).toHaveBeenCalled();
});

it('should load non imported user before adding it to the list', () => {
  const spy = jest.fn();
  const node = shallow(<UserTypeahead users={[]} onChange={spy} />);
  node.find('MultiUserInput').simulate('add', {
    id: 'kermit',
  });
  expect(getUser).toHaveBeenCalledWith('kermit');

  expect(spy).toHaveBeenCalledWith([
    {
      id: 'USER:kermit',
      identity: {id: 'kermit', name: 'Kermit'},
    },
  ]);
});

it('should handle users and collectionUsers null values', () => {
  const node = shallow(<UserTypeahead users={null} collectionUsers={[]} onChange={jest.fn()} />);
  expect(node.find(DropdownSkeleton)).toExist();

  node.setProps({users: [], collectionUsers: null});
  expect(node.find(DropdownSkeleton)).toExist();

  node.setProps({users: [], collectionUsers: []});
  expect(node.find(DropdownSkeleton)).not.toExist();
});
