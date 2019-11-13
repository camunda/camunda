/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {showError} from 'notifications';
import {deleteEntity} from 'services';
import {ConfirmationModal} from 'components';

import DeleterWithErrorHandling from './Deleter';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  deleteEntity: jest.fn()
}));

jest.mock('notifications', () => ({
  showError: jest.fn()
}));

const Deleter = DeleterWithErrorHandling.WrappedComponent;

const props = {
  mightFail: (promise, cb) => cb(promise),
  onClose: jest.fn(),
  onDelete: jest.fn()
};

const entity = {id: 'entityId', name: 'Doomed Report', entityType: 'report'};

it('should not render anything when no entity is set', () => {
  const node = shallow(<Deleter {...props} entity={null} />);

  expect(node).toMatchSnapshot();
});

it('should show the confirmation modal when entity is set', () => {
  const node = shallow(<Deleter {...props} />);
  node.setProps({entity});

  expect(node).toMatchSnapshot();
});

it('should allow to check for and display conflicts', () => {
  const conflictChecker = jest.fn().mockReturnValue({conflictedItems: ['conflict1', 'conflict2']});

  const node = shallow(<Deleter {...props} checkConflicts={conflictChecker} />);
  node.setProps({entity});

  expect(conflictChecker).toHaveBeenCalled();
});

it('should show an error message if conflict checking goes wrong', () => {
  const conflictChecker = jest.fn().mockReturnValue({conflictedItems: ['conflict1', 'conflict2']});

  const node = shallow(
    <Deleter
      {...props}
      checkConflicts={conflictChecker}
      mightFail={(promise, cb, error) => error('Everything broke')}
    />
  );
  node.setProps({entity});

  expect(showError).toHaveBeenCalledWith('Everything broke');
});

it('should delete the entity', () => {
  const node = shallow(<Deleter {...props} />);
  node.setProps({entity});

  node.find(ConfirmationModal).prop('onConfirm')();

  expect(deleteEntity).toHaveBeenCalledWith(entity.entityType, entity.id);
});

it('should accept a custom delete executor', () => {
  const spy = jest.fn();
  deleteEntity.mockClear();

  const node = shallow(<Deleter {...props} deleteEntity={spy} />);
  node.setProps({entity});

  node.find(ConfirmationModal).prop('onConfirm')();

  expect(spy).toHaveBeenCalledWith(entity);
  expect(deleteEntity).not.toHaveBeenCalled();
});

it('should show an error message if deletion goes wrong', () => {
  const node = shallow(
    <Deleter {...props} mightFail={(promise, cb, error) => error('Deleting failed')} />
  );
  node.setProps({entity});
  node.find(ConfirmationModal).prop('onConfirm')();

  expect(showError).toHaveBeenCalledWith('Deleting failed');
});

it('should call the close handler', () => {
  const spy = jest.fn();

  const node = shallow(<Deleter {...props} onClose={spy} />);
  node.setProps({entity});

  node.find(ConfirmationModal).prop('onConfirm')();

  expect(spy).toHaveBeenCalled();
});

it('should accept a custom name formatter', () => {
  const node = shallow(<Deleter {...props} getName={() => 'cool name'} />);
  node.setProps({entity});

  expect(node.find(ConfirmationModal).prop('entityName')).toBe('cool name');
});
