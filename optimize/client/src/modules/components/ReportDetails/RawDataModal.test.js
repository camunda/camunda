/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Modal} from 'components';

import RawDataModal from './RawDataModal';

const props = {
  open: true,
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should invoke onClose when closing the modal', async () => {
  const node = shallow(<RawDataModal {...props} />);

  node.find(Modal).simulate('close');

  expect(props.onClose).toHaveBeenCalled();
});
