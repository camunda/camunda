/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {UserTypeahead} from 'components';
import {getOptimizeProfile} from 'config';

import AddUserModal from './AddUserModal';

const optimizeProfile: Awaited<ReturnType<typeof getOptimizeProfile>> = 'ccsm';

const props = {
  optimizeProfile,
  open: true,
  existingUsers: [],
  onClose: jest.fn(),
  onConfirm: jest.fn(),
};

it('should render the modal properly', () => {
  const node = shallow(<AddUserModal {...props} />);

  const radioButtons = node.find('RadioButton');

  expect(node.find('UserTypeahead').prop('titleText')).toEqual('Users');

  expect(radioButtons.at(0).dive().find('span').at(1).text()).toBe('Viewer');
  expect(radioButtons.at(1).dive().find('span').at(1).text()).toBe('Editor');
  expect(radioButtons.at(2).dive().find('span').at(1).text()).toBe('Manager');
});

it('should call the onConfirm prop', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:testUser', identity: {id: 'testUser', name: ''}},
  ]);

  node.find('RadioButton').at(1).simulate('click');

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith([
    {identity: {id: 'testUser', name: ''}, role: 'editor'},
  ]);
});

it('should call onClose when the cancel button is clicked', () => {
  const wrapper = shallow(<AddUserModal {...props} />);
  const cancelButton = wrapper.find('.cancel');
  cancelButton.simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});
