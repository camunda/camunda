/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {Modal} from 'components';

import EditUserModal from './EditUserModal';

const props = {
  initialRole: 'editor',
  onClose: jest.fn(),
  onConfirm: jest.fn(),
  identity: {
    id: 'user',
    name: 'User',
  },
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render the modal properly', () => {
  const node = shallow(<EditUserModal {...props} />);

  const radioButtons = node.find('RadioButton');

  expect(radioButtons.at(0).dive().find('span').at(1).text()).toBe('Viewer');
  expect(radioButtons.at(1).dive().find('span').at(1).text()).toBe('Editor');
  expect(radioButtons.at(2).dive().find('span').at(1).text()).toBe('Manager');
});

it('should display the user name or ID', () => {
  const wrapper = shallow(<EditUserModal {...props} />);
  const header = wrapper.find(Modal.Header);
  expect(header.prop('title')).toEqual(`Edit ${props.identity.name}`);
});

it('should call the onConfirm prop', () => {
  const node = shallow(<EditUserModal {...props} />);

  const managerRadio = node.find('RadioButton').last();
  managerRadio.simulate('click');
  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('manager');
});

it('should call onClose when the cancel button is clicked', () => {
  const wrapper = shallow(<EditUserModal {...props} />);
  const cancelButton = wrapper.find('.cancel');
  cancelButton.simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});
