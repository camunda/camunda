/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {UserTypeahead} from 'components';

import AddUserModal from './AddUserModal';

const props = {
  open: true,
  existingUsers: [],
  onClose: jest.fn(),
  onConfirm: jest.fn(),
  optimizeProfile: 'platform',
};

it('should match snapshot', () => {
  const node = shallow(<AddUserModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should call the onConfirm prop', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:testUser', identity: {id: 'testUser', type: 'user'}},
  ]);
  node.setState({activeRole: 'editor'});

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith([
    {identity: {id: 'testUser', type: 'user'}, role: 'editor'},
  ]);
});
