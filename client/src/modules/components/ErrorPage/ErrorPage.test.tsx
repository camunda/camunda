/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import ErrorPage from './ErrorPage';

it('displays the error message passed in props', () => {
  const node = shallow(<ErrorPage>This is the error message.</ErrorPage>);

  expect(node.text()).toContain('This is the error message.');
  expect(node.find('h1')).toHaveText('This link is not valid.');
  expect(node.find('Link')).toHaveText('Go to Home…');
});
