/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import CopyToClipboard from './CopyToClipboard';

const props = {
  value: 'test',
  disabled: false,
  onCopy: jest.fn(),
  children: 'buttonContent',
};

it('should allow custom labels', () => {
  const node = shallow(<CopyToClipboard {...props}>Custom Label</CopyToClipboard>);

  expect(node).toIncludeText('Custom Label');
});

it('should allow being disabled', () => {
  const node = shallow(<CopyToClipboard {...props} disabled />);

  expect(node.find(Button)).toBeDisabled();
});

it('should copy specified value', () => {
  const node = shallow(<CopyToClipboard {...props} />);

  node.find(Button).simulate('click', {preventDefault: () => {}});
  expect(document.execCommand).toHaveBeenCalledWith('Copy');
});

it('should call a provided onCopy function', () => {
  const spy = jest.fn();
  const node = shallow(<CopyToClipboard {...props} onCopy={spy} />);

  node.find(Button).simulate('click', {preventDefault: () => {}});

  expect(spy).toHaveBeenCalledTimes(1);
});
