/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import AllColumnsButtons from './AllColumnsButtons';

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
