/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Logo from './Logo';

it('should render without crashing', () => {
  mount(<Logo />);
});

it('should render additional classnames', () => {
  const node = mount(<Logo className="foo" />);

  expect(node.find('.Logo')).toMatchSelector('.foo');
});

it('should pass a fill to the contained svg as provided as a property', () => {
  const node = mount(<Logo fill="#000000" />);

  expect(node.find('svg')).toMatchSelector('[fill="#000000"]');
});
