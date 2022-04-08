/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityNameForm} from './EntityNameForm';
import {Input} from 'components';

const props = {
  entity: 'Report',
  mightFail: (promise, cb) => cb(promise),
};

it('should provide name edit input', async () => {
  const node = await shallow(<EntityNameForm {...props} />);

  expect(node.find(Input)).toExist();
});

it('should provide a link to view mode', async () => {
  const node = await shallow(<EntityNameForm {...props} />);

  expect(node.find('.save-button')).toExist();
  expect(node.find('.cancel-button')).toExist();
});

it('should invoke save on save button click', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm {...props} name="" onSave={spy} />);

  node.find('.save-button').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable save button if report name is empty', async () => {
  const node = await shallow(<EntityNameForm {...props} name="" />);

  expect(node.find('.save-button')).toBeDisabled();
});

it('should call change function on input change', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm {...props} name="test name" onChange={spy} />);

  const evt = {target: {value: 'asdf'}};

  node.find(Input).simulate('change', evt);

  expect(spy).toHaveBeenCalledWith(evt);
});

it('should invoke cancel', async () => {
  const spy = jest.fn();
  const node = await shallow(<EntityNameForm {...props} onCancel={spy} />);

  await node.find('.cancel-button').simulate('click');
  expect(spy).toHaveBeenCalled();
});
