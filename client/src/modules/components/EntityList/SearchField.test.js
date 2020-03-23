/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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

it('should reset the input when clicking the cloe button', () => {
  const spy = jest.fn();
  const node = shallow(<SearchField value="content" onChange={spy} />);

  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalledWith('');
});
