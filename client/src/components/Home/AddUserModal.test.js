/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AddUserModal from './AddUserModal';

const props = {
  open: true,
  existingUsers: [],
  onClose: jest.fn(),
  onConfirm: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<AddUserModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should activate the radio button when clicking the input field', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.find('.groupIdInput').simulate('click');

  expect(node.find('.groupIdRadio')).toBeChecked();
});

it('should call the onConfirm prop', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.setState({userName: 'testUser', activeRole: 'editor'});

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('testUser', 'user', 'editor');
});

it('should show an error message when trying to add a user that already exists', () => {
  const node = shallow(
    <AddUserModal {...props} existingUsers={[{identity: {id: 'testUser', type: 'user'}}]} />
  );

  node.setState({userName: 'testUser', activeRole: 'editor'});

  expect(node.find('ErrorMessage')).toExist();
});
