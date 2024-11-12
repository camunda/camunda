/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {Deleter} from 'components';

import BulkDeleter from './BulkDeleter';

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props = {
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

  node.find('TableBatchAction').simulate('click');
  node.find(Deleter).prop('deleteEntity')();

  expect(props.deleteEntities).toHaveBeenCalledWith(props.selectedEntries);
  expect(props.onDelete).toHaveBeenCalled();
});

it('should show conflict message if a conflict has happend', () => {
  const node = shallow(
    <BulkDeleter {...props} checkConflicts={jest.fn()} conflictMessage="testMessage" />
  );

  node.find('TableBatchAction').simulate('click');
  node.find(Deleter).prop('onConflict')();

  expect(node.find(Deleter).prop('descriptionText')).toMatchSnapshot();
});

it('should check for conflicts in the selected entries', () => {
  const spy = jest.fn();
  const node = shallow(<BulkDeleter {...props} checkConflicts={spy} />);

  node.find('TableBatchAction').simulate('click');
  node.find(Deleter).prop('checkConflicts')(props.selectedEntries);

  expect(spy).toHaveBeenCalledWith(props.selectedEntries);
});
