/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Popover} from 'components';

import EntityName from './EntityName';

it('should show details content in a popover', () => {
  const node = shallow(<EntityName details="some details">My Report</EntityName>);

  expect(node.find(Popover)).toExist();
  expect(node.find(Popover).prop('children')).toBe('some details');
});
