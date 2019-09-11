/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import CollectionModalWtihErrorHandling from './CollectionModal';
import {LabeledInput} from 'components';

const CollectionModal = CollectionModalWtihErrorHandling.WrappedComponent;

const props = {
  initialName: 'aCollectionName',
  onClose: jest.fn(),
  onConfirm: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should provide name edit input', async () => {
  const node = await shallow(<CollectionModal {...props} />);
  node.setState({name: 'test name'});

  expect(node.find(LabeledInput)).toExist();
});

it('have a cancel and save collection button', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  expect(node.find('.confirm')).toExist();
  expect(node.find('.cancel')).toExist();
});

it('should invoke onConfirm on save button click', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('aCollectionName');
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<CollectionModal {...props} />);
  node.setState({name: ''});

  expect(node.find('.confirm')).toBeDisabled();
});

it('should update name on input change', async () => {
  const node = await shallow(<CollectionModal {...props} />);
  node.setState({name: 'test name'});

  const input = 'asdf';
  node.find(LabeledInput).simulate('change', {target: {value: input}});
  expect(node.state().name).toBe(input);
});

it('should invoke onClose on cancel', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  await node.find('.cancel').simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});
