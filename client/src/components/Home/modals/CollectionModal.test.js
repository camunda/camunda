/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LabeledInput} from 'components';

import {CollectionModal} from './CollectionModal';
import SourcesModal from './SourcesModal';

const props = {
  initialName: 'aCollectionName',
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should provide name edit input', async () => {
  const node = await shallow(<CollectionModal {...props} />);

  expect(node.find(LabeledInput).prop('value')).toBe(props.initialName);
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

  node.find(LabeledInput).simulate('change', {target: {value: ''}});

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
