/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Deleter, Dropdown} from 'components';

import {BulkDeleter} from './BulkDeleter';

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  deleteEntities: jest.fn(),
  onDelete: jest.fn(),
  selectedEntries: [{id: 'reportId'}],
  type: 'delete',
};

beforeEach(() => {
  props.onDelete.mockClear();
  props.deleteEntities.mockClear();
});

it('should delete selected entities, reset selected items and any conflicts on confirmation', () => {
  const node = shallow(<BulkDeleter {...props} />);

  node.find(Dropdown.Option).simulate('click');
  node.find(Deleter).prop('deleteEntity')();

  expect(props.deleteEntities).toHaveBeenCalledWith(props.selectedEntries);
  expect(props.onDelete).toHaveBeenCalled();
});

it('should show conflict message if a conflict has happend', () => {
  const node = shallow(
    <BulkDeleter {...props} checkConflicts={jest.fn()} conflictMessage="testMessage" />
  );

  node.find(Dropdown.Option).simulate('click');
  node.find(Deleter).prop('onConflict')();

  expect(node.find(Deleter).prop('descriptionText')).toMatchSnapshot();
});

it('should check for conflicts in the selected entries', () => {
  const spy = jest.fn();
  const node = shallow(<BulkDeleter {...props} checkConflicts={spy} />);

  node.find(Dropdown.Option).simulate('click');
  node.find(Deleter).prop('checkConflicts')(props.selectedEntries);

  expect(spy).toHaveBeenCalledWith(props.selectedEntries);
});
