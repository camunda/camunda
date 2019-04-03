/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Switch from './Switch';

jest.mock('components', () => {
  return {Input: props => <input {...props}>{props.children}</input>};
});

it('should render without crashing', () => {
  mount(<Switch />);
});

it('renders an <input> element by default', () => {
  const node = mount(<Switch />);

  expect(node).toHaveTagName('Switch');
});

it('should be checked/enabled if is set in the property', () => {
  const node = mount(<Switch checked={true} onChange={jest.fn()} />);

  expect(node.find('Input[type="checkbox"][checked=true]')).toHaveLength(1);
});

it('renders the id as provided as a property', () => {
  const id = 'my-switch';

  const node = mount(<Switch id={id} />);
  expect(node.find('input')).toMatchSelector('#' + id);
});

it('executes an on-change handler as provided as a property', () => {
  const handler = jest.fn();
  const node = mount(<Switch checked={true} onChange={handler} />);

  node.find('input[type="checkbox"]').simulate('change', {target: {checked: false}});
  expect(handler).toHaveBeenCalled();
});
