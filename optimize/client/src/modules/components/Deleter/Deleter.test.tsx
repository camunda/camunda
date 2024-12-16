/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {Modal} from 'components';
import {showError} from 'notifications';
import {deleteEntity} from 'services';
import {useErrorHandling} from 'hooks';
import {EntityListEntity} from 'types';

import Deleter from './Deleter';

jest.mock('hooks', () => ({
  useErrorHandling: jest
    .fn()
    .mockReturnValue({mightFail: jest.fn().mockImplementation((data, cb) => cb(data))}),
}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  deleteEntity: jest.fn(),
}));

jest.mock('notifications', () => ({
  showError: jest.fn(),
}));

const entity = {id: 'entityId', name: 'Doomed Report', entityType: 'report'} as EntityListEntity;

const props = {
  entity,
  type: 'report',
  onClose: jest.fn(),
  onDelete: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should show the confirmation modal when entity is set', () => {
  const node = shallow(<Deleter {...props} />);

  expect(node).toMatchSnapshot();
});

it('should allow to check for and display conflicts', () => {
  const conflictChecker = jest
    .fn()
    .mockReturnValue({conflictedItems: [{id: 'conflict1', type: 'dashboard', name: 'conflict1'}]});

  shallow(<Deleter {...props} checkConflicts={conflictChecker} />);
  runAllEffects();

  expect(conflictChecker).toHaveBeenCalled();
});

it('should show an error message if conflict checking goes wrong', async () => {
  const conflictChecker = jest.fn().mockReturnValue({conflictedItems: ['conflict1', 'conflict2']});
  (useErrorHandling as jest.Mock).mockReturnValueOnce({
    mightFail: (_req: unknown, _res: unknown, err: (err: string) => void) =>
      err('everything broke'),
  });

  shallow(<Deleter {...props} checkConflicts={conflictChecker} />);
  runAllEffects();

  expect(showError).toHaveBeenCalledWith('everything broke');
});

it('should delete the entity', () => {
  const node = shallow(<Deleter {...props} />);

  node.find(Modal).find('.confirm').simulate('click');

  expect(deleteEntity).toHaveBeenCalledWith(entity.entityType, entity.id);
});

it('should accept a custom delete executor', () => {
  const spy = jest.fn();
  (deleteEntity as jest.Mock).mockClear();

  const node = shallow(<Deleter {...props} deleteEntity={spy} />);

  node.find(Modal).find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith(entity);
  expect(deleteEntity).not.toHaveBeenCalled();
});

it('should show an error message if deletion goes wrong', () => {
  (useErrorHandling as jest.Mock).mockReturnValueOnce({
    mightFail: (_req: unknown, _res: unknown, err: (err: string) => void) => err('Deleting failed'),
  });

  const node = shallow(<Deleter {...props} />);

  node.find(Modal).find('.confirm').simulate('click');

  expect(showError).toHaveBeenCalledWith('Deleting failed');
});

it('should call the close handler', async () => {
  const spy = jest.fn();
  const node = shallow(<Deleter {...props} onClose={spy} />);
  runAllEffects();

  node.find(Modal).find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should accept a custom name formatter', () => {
  const node = shallow(<Deleter {...props} getName={() => 'cool name'} />);

  expect(node.find(Modal.Content)).toMatchSnapshot();
});

it('should use provided delete text for title and button', () => {
  const node = shallow(<Deleter {...props} deleteText="Remove entity" />);

  expect(node).toMatchSnapshot();
});

it('should use the provided delete button text', () => {
  const node = shallow(<Deleter {...props} deleteText="Remove entity" deleteButtonText="Delete" />);

  expect(node.find('.confirm')).toIncludeText('Delete');
});

it('should invoke onConflict if the conflict response is a boolean true', () => {
  const conflictChecker = jest.fn().mockReturnValue(true);
  const spy = jest.fn();

  shallow(<Deleter {...props} onConflict={spy} checkConflicts={conflictChecker} />);
  runAllEffects();

  expect(spy).toHaveBeenCalled();
});

it('should not show the undo warning if the reversableAction prop is set to true', () => {
  const node = shallow(<Deleter {...props} isReversableAction />);
  expect(node.find(Modal.Content).text()).not.toMatch('This action cannot be undone.');
});
