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

it('should call the onConfirm prop', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.setState({selectedIdentity: {id: 'testUser', type: 'user'}, activeRole: 'editor'});

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('testUser', 'user', 'editor');
});

it('should show an error message when trying to add a user that already exists', () => {
  const node = shallow(
    <AddUserModal {...props} existingUsers={[{identity: {id: 'testUser', type: 'user'}}]} />
  );

  node.setState({selectedIdentity: {id: 'testUser', type: 'user'}, activeRole: 'editor'});

  expect(node.find('ErrorMessage')).toExist();
});

it('should format user list information correctly', () => {
  const node = shallow(
    <AddUserModal {...props} existingUsers={[{identity: {id: 'testUser', type: 'user'}}]} />
  );

  node.setState({selectedIdentity: {id: 'testUser', type: 'user'}, activeRole: 'editor'});

  const formatter = node.find('Typeahead').props().formatter;

  expect(formatter({id: 'testUser'})).toEqual({
    subTexts: [],
    tag: false,
    text: 'testUser'
  });

  expect(formatter({id: 'testUser', email: 'testUser@test.com'})).toEqual({
    subTexts: ['testUser'],
    tag: false,
    text: 'testUser@test.com'
  });

  expect(
    formatter({id: 'groupId', name: 'groupName', email: 'group@test.com', type: 'group'})
  ).toEqual({
    subTexts: ['group@test.com', 'groupId'],
    tag: ' (User Group)',
    text: 'groupName'
  });
});
