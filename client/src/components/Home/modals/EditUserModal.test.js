/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

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

it('should match snapshot', () => {
  const node = shallow(<EditUserModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should call the onConfirm prop', () => {
  const node = shallow(<EditUserModal {...props} />);

  node.setState({role: 'manager'});

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('manager');
});
