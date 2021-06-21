/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter, Dropdown} from 'components';

import {BulkMenu} from './BulkMenu';

const props = {
  selectedEntries: [{id: 'reportId', type: 'report'}],
  onChange: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  bulkActions: [
    {type: 'delete', action: jest.fn(), checkConflicts: jest.fn(), conflictMessage: 'test'},
  ],
};

beforeEach(() => {
  props.onChange.mockClear();
  props.bulkActions[0].action.mockClear();
  props.bulkActions[0].checkConflicts.mockClear();
});

it('should show bulk operation dropdown', () => {
  const node = shallow(<BulkMenu {...props} />);

  expect(node.find(Dropdown)).toExist();
});

it('should not dropdown if there are no selected entries', () => {
  const node = shallow(<BulkMenu {...props} selectedEntries={[]} />);

  expect(node.find(Dropdown)).not.toExist();
});

it('should delete selected entities, reset selected items and any conflicts on confirmation', () => {
  const node = shallow(<BulkMenu {...props} />);

  node.find(Dropdown.Option).simulate('click');
  node.find(Deleter).prop('deleteEntity')();

  expect(props.bulkActions[0].action).toHaveBeenCalledWith(props.selectedEntries);
  expect(props.onChange).toHaveBeenCalled();
});

it('should show conflict message if a conflict has happend', () => {
  const node = shallow(<BulkMenu {...props} />);

  node.find(Dropdown.Option).simulate('click');
  node.find(Deleter).prop('onConflict')();

  expect(node.find(Deleter).prop('descriptionText')).toMatchSnapshot();
});

it('should check for conflicts in the selected entries', () => {
  const node = shallow(<BulkMenu {...props} />);

  node.find(Dropdown.Option).simulate('click');
  node.find(Deleter).prop('checkConflicts')(props.selectedEntries);

  expect(props.bulkActions[0].checkConflicts).toHaveBeenCalledWith(props.selectedEntries);
});
