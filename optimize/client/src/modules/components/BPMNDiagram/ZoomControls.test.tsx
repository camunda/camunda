/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {IconButton} from '@carbon/react';

import ZoomControls from './ZoomControls';

it('should match snapshot', () => {
  const node = shallow(<ZoomControls zoom={jest.fn()} fit={jest.fn()} />);

  expect(node).toMatchSnapshot();
});

it('should invoke zoom function on zoom button click', async () => {
  const spy = jest.fn();
  const node = shallow(<ZoomControls zoom={spy} fit={jest.fn()} />);

  node.find(IconButton).at(1).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should invoke fit function when clicking reset button', async () => {
  const spy = jest.fn();
  const node = shallow(<ZoomControls zoom={jest.fn()} fit={spy} />);

  node.find(IconButton).first().simulate('click');

  expect(spy).toHaveBeenCalled();
});
