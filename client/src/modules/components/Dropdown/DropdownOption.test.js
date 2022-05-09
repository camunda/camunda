/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Link} from 'react-router-dom';

import DropdownOption from './DropdownOption';

it('should render a link if the link prop is set', () => {
  const node = shallow(<DropdownOption link="/somewhere">Content</DropdownOption>);

  expect(node.find(Link)).toExist();
  expect(node.find(Link).prop('to')).toBe('/somewhere');
});

it('should render a normal div if it is not a link', () => {
  const spy = jest.fn();
  const node = shallow(<DropdownOption onClick={spy}>Content</DropdownOption>);

  expect(node.find('div')).toExist();

  node.find('div').simulate('click');

  expect(spy).toHaveBeenCalled();
});
