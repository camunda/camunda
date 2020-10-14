/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {default as Redirector, redirectTo} from './Redirector';

it('should not render a Redirect if not prompted', () => {
  const node = shallow(<Redirector />);

  expect(node.find('Redirect')).not.toExist();
});

it('should render a Redirect if redirectTo is called', () => {
  const node = shallow(<Redirector />);

  redirectTo('target');

  expect(node.find('Redirect')).toExist();
  expect(node.find('Redirect').prop('to')).toBe('target');
});

it('should clear the Redirect after a single render cycle', () => {
  const node = shallow(<Redirector />);

  redirectTo('target');
  runLastEffect();

  expect(node.find('Redirect')).not.toExist();
});
