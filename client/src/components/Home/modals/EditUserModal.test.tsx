/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import {CarbonModal as Modal} from 'components';

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

it('should display the user name or ID', () => {
  const wrapper = shallow(<EditUserModal {...props} />);
  const header = wrapper.find(Modal.Header);
  expect(header.text()).toEqual(`Edit ${props.identity.name}`);
});

it('should call the onConfirm prop', () => {
  const node = shallow(<EditUserModal {...props} />);

  const editorRadio = node.find({type: 'radio'}).last();
  editorRadio.simulate('change');
  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('manager');
});

it('should call onClose when the cancel button is clicked', () => {
  const wrapper = shallow(<EditUserModal {...props} />);
  const cancelButton = wrapper.find(Button).first();
  cancelButton.simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});
