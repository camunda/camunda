/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';

import BulkMenu from './BulkMenu';

const props = {
  selectedEntries: [{id: 'reportId', type: 'report'}],
  onChange: jest.fn(),
};

beforeEach(() => {
  props.onChange.mockClear();
});

it('should show bulk operation dropdown', () => {
  const node = shallow(<BulkMenu {...props} />);

  expect(node.find(Dropdown)).toExist();
});

it('should not show a dropdown if there are no selected entries', () => {
  const node = shallow(<BulkMenu {...props} selectedEntries={[]} />);

  expect(node.find(Dropdown)).not.toExist();
});

it('should pass selected entries to dropdown options', () => {
  const node = shallow(<BulkMenu {...props} bulkActions={<div className="option" />} />);

  expect(node.find('.option').prop('selectedEntries')).toBe(props.selectedEntries);
});
