/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter, UserTypeahead} from 'components';

import EditOwnerModal from './EditOwnerModal';

it('should load the initial user into the user dropdown', () => {
  const node = shallow(<EditOwnerModal initialOwner={{id: 'test', name: 'testName'}} />);

  expect(node.find(UserTypeahead).prop('users')).toEqual([
    {id: 'USER:test', identity: {id: 'test', name: 'testName'}},
  ]);
});

it('should invoke the onConfirm with the selected user id', () => {
  const spy = jest.fn();
  const node = shallow(<EditOwnerModal onConfirm={spy} />);

  node
    .find(UserTypeahead)
    .simulate('change', [{id: 'USER:test', identity: {id: 'test', name: 'testName'}}]);

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith('test');
});

it('should invoke onConfirm with null parameter when deleting the intial owner', async () => {
  const spy = jest.fn();
  const node = shallow(
    <EditOwnerModal onConfirm={spy} initialOwner={{id: 'test', name: 'testName'}} />
  );

  node.find('.deleteButton').simulate('click');

  await node.find(Deleter).prop('deleteEntity')();

  expect(spy).toHaveBeenCalledWith(null);
});
