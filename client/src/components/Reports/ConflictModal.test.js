/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import ConflictModal from './ConflictModal';

const props = {
  conflict: {alert: [{id: '1', name: 'alert', type: 'alert'}], combined_report: []},
  onClose: jest.fn(),
  onConfirm: jest.fn(),
};

it('should match snapshot', () => {
  const node = shallow(<ConflictModal {...props} />);

  expect(node).toMatchSnapshot();
});
