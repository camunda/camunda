/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {UserTypeahead} from 'components';
import {getOptimizeProfile} from 'config';

import AddUserModal from './AddUserModal';

const optimizeProfile: Awaited<ReturnType<typeof getOptimizeProfile>> = 'platform';

const props = {
  optimizeProfile,
  open: true,
  existingUsers: [],
  onClose: jest.fn(),
  onConfirm: jest.fn(),
};

it('should match snapshot', () => {
  const node = shallow(<AddUserModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should call the onConfirm prop', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:testUser', identity: {id: 'testUser', type: 'user', name: ''}},
  ]);

  node.find({type: 'radio'}).at(1).simulate('change');

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith([
    {identity: {id: 'testUser', type: 'user', name: ''}, role: 'editor'},
  ]);
});
