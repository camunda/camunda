/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter} from 'components';

import {deleteEntities, checkConflicts} from './service';
import {BulkMenu} from './BulkMenu';

jest.mock('./service', () => ({
  deleteEntities: jest.fn(),
  checkConflicts: jest.fn(),
}));

const props = {
  selectedEntries: [{id: 'reportId', type: 'report'}],
  onChange: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  props.onChange.mockClear();
});

it('should show bulk operation dropdown', () => {
  const node = shallow(<BulkMenu {...props} />);

  expect(node.find('Dropdown')).toMatchSnapshot();
});

it('should not dropdown if there are no selected entries', () => {
  const node = shallow(<BulkMenu {...props} selectedEntries={[]} />);

  expect(node.find('Dropdown')).not.toExist();
});

it('should delete selected entities, reset selected items and any conflicts on confirmation', () => {
  const node = shallow(<BulkMenu {...props} />);

  node.find(Deleter).prop('deleteEntity')();

  expect(deleteEntities).toHaveBeenCalledWith(props.selectedEntries);
  expect(props.onChange).toHaveBeenCalled();
});

it('should show conflict message if a conflict has happend', () => {
  const node = shallow(<BulkMenu {...props} />);

  node.find(Deleter).prop('onConflict')([{type: 'report'}]);

  expect(node.find(Deleter).prop('descriptionText')).toMatchSnapshot();
});

it('should check for conflicts in the selected entries', () => {
  const node = shallow(<BulkMenu {...props} />);

  node.find(Deleter).prop('checkConflicts')();

  expect(checkConflicts).toHaveBeenCalledWith(props.selectedEntries);
});
