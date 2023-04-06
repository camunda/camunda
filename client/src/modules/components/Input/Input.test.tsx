/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow, mount} from 'enzyme';

import Input from './Input';

it('should render without crashing', () => {
  shallow(<Input />);
});

it('should render a type="text" attribute when no other type prop is provided', () => {
  const node = shallow(<Input />);

  expect(node.find('input')).toMatchSelector('input[type="text"]');
});

it('should render a type attribute provided as a property', () => {
  const node = shallow(<Input type="password" />);

  expect(node.find('input')).toMatchSelector('input[type="password"]');
});

it('should render a disabled attribute provided as a property', () => {
  const node = shallow(<Input disabled />);

  expect(node.find('input')).toMatchSelector('input[disabled]');
});

it('should merge and render additonal classNames provided as a property', () => {
  const node = shallow(<Input className="foo" />);

  expect(node.find('input')).toMatchSelector('.Input.foo');
});

it('should translate the isInvalid props to is-invalid className', () => {
  const node = shallow(<Input className="foo" isInvalid />);

  expect(node.find('input')).toHaveClassName('isInvalid');
});

it('should show a clear button when adding onClear and the input is not empty', () => {
  const node = shallow(<Input value="not empty" onClear={jest.fn()} />);
  expect(node.find('.searchClear')).toExist();
});

it('should invoke onClear when clear button is clicked', () => {
  const spy = jest.fn();
  const node = shallow(<Input value="not empty" onClear={spy} />);
  node.find('.searchClear').simulate('mousedown', {type: '', preventDefault: jest.fn()});
  expect(spy).toHaveBeenCalled();
});

it('should focus on input when clicking the clear button', () => {
  const container = document.createElement('div');
  document.body.append(container);

  const spy = jest.fn();
  const node = mount(<Input onClear={jest.fn()} ref={spy} />, {attachTo: container});
  node.find('.searchClear').simulate('mousedown');
  expect(document.activeElement?.getAttribute('class')).toBe('Input');
  expect(spy).toHaveBeenCalled();
});

it('should throw en error if you pass placeholder thats nor a string', () => {
  expect(() => shallow(<Input placeholder={[<div />]} />)).toThrow(
    'Input: Placeholder should be of type string'
  );
});
