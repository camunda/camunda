/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import FiltersView from './FiltersView';

it('should render a filter input based on the availableFilters', () => {
  const node = shallow(<FiltersView availableFilters={[{type: 'state'}]} />);

  expect(node.find('InstanceStateFilter')).toExist();
});
