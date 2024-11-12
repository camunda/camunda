/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
