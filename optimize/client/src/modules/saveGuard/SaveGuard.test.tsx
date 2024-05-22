/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {Prompt} from 'react-router-dom';
import {Button} from '@carbon/react';

import {Modal} from 'components';
import {default as SaveGuard, nowDirty} from './SaveGuard';

it('should pass dirty state to Prompt', () => {
  const node = shallow(<SaveGuard />);

  expect(node.find(Prompt)).toExist();
  expect(node.find(Prompt).prop('when')).toBe(false);

  nowDirty();

  expect(node.find(Prompt).prop('when')).toBe(true);
});

it('should show a confirmation modal when user confirmation is required and state is dirty', () => {
  const cb = jest.fn();

  const node = shallow(<SaveGuard />);

  nowDirty();
  SaveGuard.getUserConfirmation('', cb);

  expect(node.find(Modal).prop('open')).toBeTruthy();
});

it('should call the provided save handler', () => {
  const cb = jest.fn();
  const save = jest.fn();

  const node = shallow(<SaveGuard />);

  nowDirty('report', save);
  SaveGuard.getUserConfirmation('', cb);

  node.find(Modal).find(Button).last().simulate('click');

  expect(save).toHaveBeenCalled();
});

it('should allow abortion of navigation', () => {
  const cb = jest.fn();
  const save = jest.fn();

  const node = shallow(<SaveGuard />);

  nowDirty('report', save);
  SaveGuard.getUserConfirmation('', cb);

  node.find(Modal).simulate('close');

  expect(save).not.toHaveBeenCalled();
  expect(cb).toHaveBeenCalledWith(false);
});
