/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import CopyToClipboard from './CopyToClipboard';

it('should allow custom labels', () => {
  const node = shallow(<CopyToClipboard>Custom Label</CopyToClipboard>);

  expect(node).toIncludeText('Custom Label');
});

it('should allow being disabled', () => {
  const node = shallow(<CopyToClipboard disabled />);

  expect(node.find(Button)).toBeDisabled();
});

it('should copy specified value', () => {
  const node = shallow(<CopyToClipboard />);

  node.find(Button).simulate('click', {preventDefault: () => {}});
  expect(document.execCommand).toHaveBeenCalledWith('Copy');
});

it('should call a provided onCopy function', () => {
  const spy = jest.fn();
  const node = shallow(<CopyToClipboard onCopy={spy} />);

  node.find(Button).simulate('click', {preventDefault: () => {}});

  expect(spy).toHaveBeenCalledTimes(1);
});
