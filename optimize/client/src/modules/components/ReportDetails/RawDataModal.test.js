/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
