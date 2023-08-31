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

it('should render the modal properly', () => {
  const node = shallow(<AddUserModal {...props} />);

  const radioButtons = node.find('RadioButton');

  expect(node.find('UserTypeahead').prop('titleText')).toEqual('Users and User Groups');

  expect(radioButtons.at(0).dive().find('span').at(1).text()).toBe('Viewer');
  expect(radioButtons.at(1).dive().find('span').at(1).text()).toBe('Editor');
  expect(radioButtons.at(2).dive().find('span').at(1).text()).toBe('Manager');
});

it('should call the onConfirm prop', () => {
  const node = shallow(<AddUserModal {...props} />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:testUser', identity: {id: 'testUser', type: 'user', name: ''}},
  ]);

  node.find('RadioButton').at(1).simulate('click');

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith([
    {identity: {id: 'testUser', type: 'user', name: ''}, role: 'editor'},
  ]);
});

it('should call onClose when the cancel button is clicked', () => {
  const wrapper = shallow(<AddUserModal {...props} />);
  const cancelButton = wrapper.find('.cancel');
  cancelButton.simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});
