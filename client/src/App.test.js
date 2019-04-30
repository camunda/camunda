/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import App from './App';

it('should include a header for the Alert page', () => {
  const node = shallow(<App />);
  const content = shallow(node.find('Route').prop('render')({location: {pathname: '/'}}));

  expect(content.find('withRouter(Header)')).toExist();
  expect(content.find('Footer')).toExist();
});

it('should not include a header for shared resources', () => {
  const node = shallow(<App />);
  const content = shallow(
    node.find('Route').prop('render')({location: {pathname: '/share/report/3'}})
  );

  expect(content.find('Header')).not.toExist();
  expect(content.find('Footer')).not.toExist();
});
