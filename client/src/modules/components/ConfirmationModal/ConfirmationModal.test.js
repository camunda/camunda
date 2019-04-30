/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Modal, Button} from 'components';

import ConfirmationModal from './ConfirmationModal';

it('should have a closed modal when open is false', () => {
  const props = {
    open: false,
    entityName: '',
    onConfirm: () => {},
    onClose: () => {}
  };

  const node = shallow(<ConfirmationModal {...props} />);
  expect(node.find('Modal').props().open).toBeFalsy();
});

it('should show a loading indicator', () => {
  const props = {
    open: true,
    loading: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: () => {}
  };

  const node = shallow(<ConfirmationModal {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show the name of the Entity to delete', () => {
  const props = {
    open: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: () => {}
  };
  const node = shallow(<ConfirmationModal {...props} />);

  expect(node.find(Modal.Header).dive()).toIncludeText('test');
  expect(node.find('Modal').props().open).toBeTruthy();
});

it('should invok onClose when cancel button is clicked', async () => {
  const spy = jest.fn();
  const props = {
    open: true,
    entityName: 'test',
    onConfirm: () => {},
    onClose: spy
  };
  const node = shallow(<ConfirmationModal {...props} />);

  node
    .find(Button)
    .first()
    .simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should invok confirmModal when confirm button is clicked', async () => {
  const spy = jest.fn();
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    open: true,
    entityName: testEntity.name,
    onConfirm: spy,
    onClose: () => {}
  };
  const node = shallow(<ConfirmationModal {...props} />);

  node
    .find(Button)
    .at(1)
    .simulate('click');
  await node.update();

  expect(spy).toHaveBeenCalled();
});

it('should show default operation text if conflict is not set', async () => {
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    open: true,
    entityName: testEntity.name,
    onConfirm: () => {},
    onClose: () => {}
  };
  const node = shallow(<ConfirmationModal {...props} />);

  expect(node.find(Button).at(1)).toIncludeText('Delete');
});

it('should show conflict information if conflict prop is set', async () => {
  const testEntity = {name: 'test', id: 'testId'};
  const props = {
    open: true,
    entityName: testEntity.name,
    onConfirm: () => {},
    onClose: () => {},
    conflict: {type: 'Save', items: [{id: '1', name: 'testAlert', type: 'entityType'}]}
  };
  const node = shallow(<ConfirmationModal {...props} />);

  expect(node.find('li')).toIncludeText('testAlert');
  expect(node.find(Button).at(1)).toIncludeText('Save');
});
