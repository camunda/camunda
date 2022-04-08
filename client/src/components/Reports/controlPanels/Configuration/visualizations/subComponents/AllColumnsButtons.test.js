/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AllColumnsButtons from './AllColumnsButtons';
import {Button} from 'components';

it('should invoke enable All when enable all button is clicked', () => {
  const spy = jest.fn();
  const node = shallow(<AllColumnsButtons enableAll={spy} disableAll={() => {}} />);
  node.find(Button).at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should call disableAll when clicking disable all', () => {
  const spy = jest.fn();
  const node = shallow(<AllColumnsButtons enableAll={() => {}} disableAll={spy} />);

  node.find(Button).at(1).simulate('click');

  expect(spy).toHaveBeenCalled();
});
