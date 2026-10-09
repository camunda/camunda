/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import ForwardRefLink from './ForwardRefLink';

it('should route in-app destinations through react-router', () => {
  const node = shallow(<ForwardRefLink to="/collections" />);

  expect(node.find('Link').prop('to')).toBe('/collections');
});

it('should route an href that stays inside the app', () => {
  const node = shallow(<ForwardRefLink href="/analysis" />);

  expect(node.find('Link').prop('to')).toBe('/analysis');
});

it('should bypass the router for cross-app links', () => {
  const node = shallow(<ForwardRefLink href="https://operate.example.com" />);

  expect(node.find('Link')).toHaveLength(0);
  expect(node.find('a').prop('href')).toBe('https://operate.example.com');
});
