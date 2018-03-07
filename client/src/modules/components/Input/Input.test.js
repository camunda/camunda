import React from 'react';
import {mount} from 'enzyme';

import Input from './Input';

it('should render without crashing', () => {
  mount(<Input />);
});

it('should render a type="text" attribute when no other type prop is provided', () => {
  const node = mount(<Input />);

  expect(node.find('input')).toMatchSelector('input[type="text"]');
});

it('should render a type attribute provided as a property', () => {
  const node = mount(<Input type="password" />);

  expect(node.find('input')).toMatchSelector('input[type="password"]');
});

it('should render a disabled attribute provided as a property', () => {
  const node = mount(<Input disabled="disabled" />);

  expect(node.find('input')).toMatchSelector('input[disabled="disabled"]');
});

it('should merge and render additonal classNames provided as a property', () => {
  const node = mount(<Input className="foo" />);

  expect(node.find('input')).toMatchSelector('.Input.foo');
});
