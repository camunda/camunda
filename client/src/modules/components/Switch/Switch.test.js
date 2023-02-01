/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Switch from './Switch';
import {Input} from 'components';

it('should render without crashing', () => {
  shallow(<Switch />);
});

it('renders an <input> element by default', () => {
  const node = shallow(<Switch />);

  expect(node.find(Input)).toExist();
});

it('should be checked/enabled if is set in the property', () => {
  const node = shallow(<Switch checked={true} onChange={jest.fn()} />);

  expect(node.find({type: 'checkbox', checked: true})).toHaveLength(1);
});

it('renders the id as provided as a property', () => {
  const id = 'my-switch';

  const node = shallow(<Switch id={id} />);
  expect(node.find(Input).props().id).toBe(id);
});

it('executes an on-change handler as provided as a property', () => {
  const handler = jest.fn();
  const node = shallow(<Switch checked={true} onChange={handler} />);

  node.find(Input).simulate('change', {target: {checked: false}});
  expect(handler).toHaveBeenCalled();
});

it('should show the label on the right side', () => {
  const node = shallow(<Switch label="some label" />);

  const rightlabel = node.find('label').childAt(2);
  const slider = node.find('.Switch__Slider--round');

  expect(rightlabel.text()).toBe('some label');
  expect(slider.hasClass('right')).toBe(true);
});

it('should show the label on the left side', () => {
  const node = shallow(<Switch label="some label" labelPosition="left" />);

  const rightlabel = node.find('label').childAt(0);
  const slider = node.find('.Switch__Slider--round');

  expect(rightlabel.text()).toBe('some label');
  expect(slider.hasClass('left')).toBe(true);
});
