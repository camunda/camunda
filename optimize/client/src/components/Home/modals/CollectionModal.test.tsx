/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {CollectionModal} from './CollectionModal';
import SourcesModal from './SourcesModal';

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props = {
  initialName: 'aCollectionName',
  onClose: jest.fn(),
  confirmText: 'confirm',
  onConfirm: jest.fn(),
};

it('should provide name edit input', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  expect(node.find('TextInput').prop('value')).toBe(props.initialName);
});

it('have a cancel and save collection button', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  expect(node.find('.confirm')).toExist();
  expect(node.find('.cancel')).toExist();
});

it('should invoke onConfirm on save button click', async () => {
  const spy = jest.fn();
  const node = await shallow(<CollectionModal {...props} onConfirm={spy} />);

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith('aCollectionName');
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  node.find('TextInput').simulate('change', {target: {value: ''}});

  expect(node.find('.confirm')).toBeDisabled();
});

it('should invoke onClose on cancel', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  await node.find('.cancel').simulate('click');
  expect(props.onClose).toHaveBeenCalled();
});

it('should show sources modal after confirm collection creation', async () => {
  const spy = jest.fn();
  const node = await shallow(<CollectionModal {...props} onConfirm={spy} showSourcesModal />);

  node.find('.confirm').simulate('click');
  expect(spy).not.toHaveBeenCalledWith('aCollectionName');

  node.find(SourcesModal).simulate('confirm');
  expect(spy).toHaveBeenCalledWith('aCollectionName');
});
