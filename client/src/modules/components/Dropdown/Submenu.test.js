/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Submenu from './Submenu';
import DropdownOption from './DropdownOption';
import {findLetterOption} from './service';

jest.mock('./DropdownOption', () => {
  return props => {
    return (
      <div tabIndex="0" className="DropdownOption">
        {props.children}
      </div>
    );
  };
});

jest.mock('./service', () => ({findLetterOption: jest.fn()}));

console.error = jest.fn();

it('should render a dropdown option with the provided label', () => {
  const node = mount(<Submenu label="my label" />);

  expect(node.find('.DropdownOption')).toExist();
  expect(node.find('.DropdownOption')).toIncludeText('my label');
});

it('should render its child content when it is open', () => {
  const node = mount(<Submenu label="my label">SubmenuContent</Submenu>);

  expect(node).not.toIncludeText('SubmenuContent');

  node.setProps({open: true});

  expect(node).toIncludeText('SubmenuContent');
});

it('should change focus after pressing an arrow key', () => {
  const node = mount(
    <Submenu label="Click me">
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>
  );

  node.setProps({open: true});

  const container = node.find('.childrenContainer');

  container
    .find(DropdownOption)
    .first()
    .getDOMNode()
    .focus();

  container.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('bar');
  container.simulate('keyDown', {key: 'ArrowUp'});
  expect(document.activeElement.textContent).toBe('foo');
});

it('should close the submenu when left arrow is pressed', () => {
  const spy = jest.fn();
  const node = mount(
    <Submenu label="Click me" forceToggle={spy}>
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>
  );

  node.setProps({open: true});

  const container = node.find('.childrenContainer');

  container.simulate('keyDown', {key: 'ArrowLeft'});

  expect(spy).toHaveBeenCalled();
});

it('should invoke findLetterOption when typing a character', () => {
  const node = mount(<Submenu open={true} />);

  node.instance().containerRef = {
    current: {
      querySelectorAll: () => []
    }
  };

  const container = node.find('.childrenContainer');

  container.simulate('keyDown', {key: 'f', keyCode: 70});
  expect(findLetterOption.mock.calls[0][1]).toBe('f');
  expect(findLetterOption.mock.calls[0][2]).toBe(0);
});
