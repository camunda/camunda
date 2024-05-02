/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    view: {type: 'variable'},
    definitions: [{id: 'definitionId'}],
  },
};

it('should disable variable submenu if there are no number variables', () => {
  reportConfig.process.view = [
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
