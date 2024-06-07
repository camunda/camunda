/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import CopyAlertModal from './CopyAlertModal';

const props = {
  initialAlertName: 'test',
  onConfirm: jest.fn(),
  onClose: jest.fn(),
};

it('should update the alert name', () => {
  const node = shallow(<CopyAlertModal {...props} />);

  expect(node.find('TextInput').prop('value')).toBe('test (copy)');

  node.find('TextInput').simulate('change', {target: {value: 'new alert'}});
  node.find('Button').at(1).simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('new alert');
});
