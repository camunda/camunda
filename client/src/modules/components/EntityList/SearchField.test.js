/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button, Input} from 'components';

import SearchField from './SearchField';

it('should show the button according to the current open state', () => {
  const node = shallow(<SearchField />);

  expect(node.find('[type="search"]').hasClass('hidden')).toBe(false);
  expect(node.find('[type="search-reset"]').hasClass('hidden')).toBe(true);

  node.find(Button).simulate('click');

  expect(node.find('[type="search"]').hasClass('hidden')).toBe(true);
  expect(node.find('[type="search-reset"]').hasClass('hidden')).toBe(false);
});

it('should call the onChange handler when writing in the input field', () => {
  const spy = jest.fn();
  const node = shallow(<SearchField onChange={spy} />);

  node.find(Input).simulate('change', {target: {value: 'new value'}});

  expect(spy).toHaveBeenCalledWith('new value');
});

it('should reset the input when clicking the close button', () => {
  const spy = jest.fn();
  const node = shallow(<SearchField value="content" onChange={spy} />);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalledWith('');
});

it('should reset the input when pressing escape', () => {
  const spy = jest.fn();
  const node = shallow(<SearchField value="content" onChange={spy} />);

  node.find(Input).simulate('keydown', {key: 'Escape'});

  expect(spy).toHaveBeenCalledWith('');
});
