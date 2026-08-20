/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {reportConfig} from 'services';
import {Select} from 'components';

import View from './View';

const config = {
  type: 'process',
  variables: [{id: 'test', type: 'date', name: 'testName'}],
  onChange: jest.fn(),
  report: {
    view: {type: 'variable', properties: []},
    definitions: [{id: 'definitionId'}],
  },
};

it('should disable variable submenu if there are no number variables', () => {
  reportConfig.view = [
    {
      key: 'variable',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Variable'),
    },
  ];

  const node = shallow(<View {...config} />);

  expect(node.find(Select.Submenu).prop('value')).toBe('variable');
  expect(node.find(Select.Submenu).prop('disabled')).toBe(true);
});
