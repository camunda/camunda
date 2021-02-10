/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {IncompleteReport} from './IncompleteReport';

it('should render a router link for non shared entities with a returnTo parameter', () => {
  delete window.location;
  window.location = new URL('http://example.com/dashboard/dashboardId');

  const node = shallow(
    <IncompleteReport id="reportId" location={{pathname: '/dashboard/dashboardId'}} />
  );

  expect(node.find('Link').prop('to')).toBe(
    '/report/reportId/edit?returnTo=/dashboard/dashboardId'
  );
});

it('should render an href for shared reports that links to report edit mode ignoring /external sub url', () => {
  delete window.location;
  window.location = new URL('http://example.com/subUrl/external/#/share/dashboard/shareId');

  const node = shallow(<IncompleteReport id="reportId" location="" />);

  expect(node.find('a').prop('href')).toBe('http://example.com/subUrl/#/report/reportId/edit');
});
