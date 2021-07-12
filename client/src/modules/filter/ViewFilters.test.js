/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ViewFilters from './ViewFilters';

const props = {
  openNewFilterModal: () => {},
  definitions: [{}],
  processDefinitionIsNotSelected: false,
};

it('should disable dropdown for multi-measure reports', () => {
  const node = shallow(<ViewFilters {...props} definitions={[{}, {}]} />);

  expect(node.find('Dropdown').prop('disabled')).toBe(true);
});

it('should show a tooltip if the dropdown is disabled', () => {
  const node = shallow(<ViewFilters {...props} definitions={[{}, {}]} />);

  expect(node.find('Tooltip')).toExist();
});
