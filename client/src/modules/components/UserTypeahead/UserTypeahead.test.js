/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {showError} from 'notifications';

import {UserTypeahead} from './UserTypeahead';
import {getUser} from './service';

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('./service', () => ({
  getUser: jest.fn().mockReturnValue({id: 'kermit', type: 'user', name: 'Kermit'}),
}));

it('should invoke onChange when adding a user', () => {
  const spy = jest.fn();
  const node = shallow(
    <UserTypeahead
      users={[{id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}}]}
      onChange={spy}
    />
  );

  node.find('MultiUserInput').prop('onAdd')({
    id: 'sales',
    type: 'group',
    name: 'Sales',
    memberCount: '2',
  });

  expect(spy).toHaveBeenCalledWith([
    {id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}},
    {id: 'GROUP:sales', identity: {id: 'sales', memberCount: '2', name: 'Sales', type: 'group'}},
  ]);
});

it('should show an error when adding already existing user/group', () => {
  const node = shallow(
    <UserTypeahead
      users={[{id: 'USER:kermit', identity: {id: 'kermit', type: 'user'}}]}
      mightFail={jest.fn().mockImplementation((data, cb) => cb(data))}
      onChange={jest.fn()}
    />
  );

  node.find('MultiUserInput').prop('onAdd')({id: 'kermit'});

  expect(showError).toHaveBeenCalled();
});

it('should load non imported user before adding it to the list', () => {
  const spy = jest.fn();
  const node = shallow(
    <UserTypeahead
      users={[]}
      mightFail={jest.fn().mockImplementation((data, cb) => cb(data))}
      onChange={spy}
    />
  );
  node.find('MultiUserInput').prop('onAdd')({
    id: 'kermit',
  });
  expect(getUser).toHaveBeenCalledWith('kermit');

  expect(spy).toHaveBeenCalledWith([
    {
      id: 'USER:kermit',
      identity: {id: 'kermit', memberCount: undefined, name: 'Kermit', type: 'user'},
    },
  ]);
});
