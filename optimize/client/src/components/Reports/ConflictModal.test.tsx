/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import ConflictModal from './ConflictModal';

const props = {
  conflicts: [{id: '1', name: 'alert', type: 'alert'}],
  onClose: jest.fn(),
  onConfirm: jest.fn(),
};

it('should match snapshot', () => {
  const node = shallow(<ConflictModal {...props} />);

  expect(node).toMatchSnapshot();
});
