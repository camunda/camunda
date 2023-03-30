/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from '@carbon/react';

import {default as Prompt, showPrompt} from './Prompt';

const promptText = {
  title: 'Prompt Title',
  body: 'This is a test prompt',
  yes: 'Okay',
  no: 'Please dont',
};

it('should not show an open Modal when mounted', () => {
  const node = shallow(<Prompt />);

  expect(node.find('Modal').prop('open')).toBe(false);
});

it('should open a modal with custom text when calling showPrompt', () => {
  const node = shallow(<Prompt />);

  showPrompt(promptText, () => {});

  expect(node.find('Modal').prop('open')).toBe(true);
});

it('should not do anything if the user closes the modal again', async () => {
  const node = shallow(<Prompt />);

  const spy = jest.fn();
  showPrompt(promptText, spy);
  node.find(Button).at(0).simulate('click');

  await flushPromises();

  expect(node.find('Modal').prop('open')).toBe(false);
  expect(spy).not.toHaveBeenCalled();
});

it('should call the callback when the user confirms the prompt', async () => {
  const node = shallow(<Prompt />);

  const spy = jest.fn();
  showPrompt(promptText, spy);
  node.find(Button).at(1).simulate('click');

  await flushPromises();

  expect(node.find('Modal').prop('open')).toBe(false);
  expect(spy).toHaveBeenCalled();
});
