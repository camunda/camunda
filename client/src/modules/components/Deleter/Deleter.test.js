/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {CarbonModal as Modal} from 'components';
import {showError} from 'notifications';
import {deleteEntity} from 'services';

import DeleterWithErrorHandling from './Deleter';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  deleteEntity: jest.fn(),
}));

jest.mock('notifications', () => ({
  showError: jest.fn(),
}));

const Deleter = DeleterWithErrorHandling.WrappedComponent;

const props = {
  type: 'report',
  mightFail: (promise, cb) => cb(promise),
  onClose: jest.fn(),
  onDelete: jest.fn(),
};

const entity = {id: 'entityId', name: 'Doomed Report', entityType: 'report'};
function setupRef(node) {
  node.instance().cancelButton = {current: {focus: () => {}}};
}

it('should not render anything when no entity is set', () => {
  const node = shallow(<Deleter {...props} entity={null} />);

  expect(node).toMatchSnapshot();
});

it('should show the confirmation modal when entity is set', () => {
  const node = shallow(<Deleter {...props} />);
  setupRef(node);

  node.setProps({entity});

  expect(node).toMatchSnapshot();
});

it('should allow to check for and display conflicts', () => {
  const conflictChecker = jest
    .fn()
    .mockReturnValue({conflictedItems: [{id: 'conflict1', type: 'dashboard', name: 'conflict1'}]});

  const node = shallow(<Deleter {...props} checkConflicts={conflictChecker} />);
  setupRef(node);

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
  setupRef(node);

  node.setProps({entity});

  expect(showError).toHaveBeenCalledWith('Everything broke');
});

it('should delete the entity', () => {
  const node = shallow(<Deleter {...props} />);
  setupRef(node);

  node.setProps({entity});

  node.find(Modal).prop('onConfirm')();

  expect(deleteEntity).toHaveBeenCalledWith(entity.entityType, entity.id);
});

it('should accept a custom delete executor', () => {
  const spy = jest.fn();
  deleteEntity.mockClear();

  const node = shallow(<Deleter {...props} deleteEntity={spy} />);
  setupRef(node);

  node.setProps({entity});

  node.find(Modal).prop('onConfirm')();

  expect(spy).toHaveBeenCalledWith(entity);
  expect(deleteEntity).not.toHaveBeenCalled();
});

it('should show an error message if deletion goes wrong', () => {
  const node = shallow(
    <Deleter {...props} mightFail={(promise, cb, error) => error('Deleting failed')} />
  );
  setupRef(node);

  node.setProps({entity});
  node.find(Modal).prop('onConfirm')();

  expect(showError).toHaveBeenCalledWith('Deleting failed');
});

it('should call the close handler', () => {
  const spy = jest.fn();

  const node = shallow(<Deleter {...props} onClose={spy} />);
  setupRef(node);

  node.setProps({entity});

  node.find(Modal).prop('onConfirm')();

  expect(spy).toHaveBeenCalled();
});

it('should accept a custom name formatter', () => {
  const node = shallow(<Deleter {...props} getName={() => 'cool name'} />);
  setupRef(node);

  node.setProps({entity});

  expect(node.find(Modal.Content)).toMatchSnapshot();
});

it('should use provided delete text for title and button', () => {
  const node = shallow(<Deleter {...props} deleteText="Remove entity" />);
  setupRef(node);

  node.setProps({entity});

  expect(node).toMatchSnapshot();
});

it('should use the provided delete button text', () => {
  const node = shallow(<Deleter {...props} deleteText="Remove entity" deleteButtonText="Delete" />);
  setupRef(node);

  node.setProps({entity});

  expect(node.find('.confirm')).toIncludeText('Delete');
});

it('should invoke onConflict if the conflict response is a boolean true', () => {
  const conflictChecker = jest.fn().mockReturnValue(true);
  const spy = jest.fn();

  const node = shallow(<Deleter {...props} onConflict={spy} checkConflicts={conflictChecker} />);

  node.setProps({entity});

  expect(spy).toHaveBeenCalled();
});

it('should not show the undo warning if the reversableAction prop is set to true', () => {
  const node = shallow(<Deleter {...props} isReversableAction />);
  setupRef(node);
  node.setProps({entity});

  expect(node.find(Modal.Content).html()).not.toMatch('This action cannot be undone.');
});
