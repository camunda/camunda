/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import ColorPicker from './ColorPicker';

it('should include 16 colors by default', () => {
  const node = shallow(<ColorPicker onChange={() => {}} />);

  expect(node.find('.color').length).toBe(16);
});

it('should add class active to the selected color', () => {
  const node = shallow(<ColorPicker selectedColor="#FEF3BD" onChange={() => {}} />);
  expect(node.find('.active').props().color).toBe('#FEF3BD');
});

it('should invoke onChange when a color is selected', () => {
  const spy = jest.fn();
  const node = shallow(<ColorPicker onChange={spy} />);

  node
    .find('.color')
    .first()
    .simulate('click', {target: {getAttribute: () => 'testColor'}});

  expect(spy).toHaveBeenCalledWith('testColor');
});

it('should generate correct amount of colors', () => {
  const colors = ColorPicker.getGeneratedColors(18);
  expect(colors.length).toBe(18);
  expect(colors[17]).toBe('#54e09c');
});

it('should should repeat generated colors if they are not enough', () => {
  const colors = ColorPicker.getGeneratedColors(200);
  expect(colors.length).toBe(200);
  expect(colors[63]).toBe(colors[0]);
});
