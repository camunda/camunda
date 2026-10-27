/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import ErrorPage from './ErrorPage';

it('displays the error message passed in props', () => {
  const node = shallow(<ErrorPage>This is the error message.</ErrorPage>);

  expect(node.text()).toContain('This is the error message.');
  expect(node.find('h1')).toHaveText('This link is not valid.');
  expect(node.find('Link')).toHaveText('Go to Home…');
});
