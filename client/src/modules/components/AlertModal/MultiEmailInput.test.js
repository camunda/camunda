/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {MultiValueInput} from 'components';

import MultiEmailInput from './MultiEmailInput';

const props = {
  emails: ['email1@hotmail.com', 'email2@gmail.com'],
  onChange: jest.fn(),
};

beforeEach(() => props.onChange.mockClear());

it('should match snapshot', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  expect(node).toMatchSnapshot();
});

it('should add an email', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find(MultiValueInput).prop('onAdd')('test@test.com');

  expect(props.onChange).toHaveBeenCalledWith([...props.emails, 'test@test.com'], true);

  props.onChange.mockClear();

  node.find(MultiValueInput).prop('onAdd')('invalid');

  expect(props.onChange).toHaveBeenCalledWith([...props.emails, 'invalid'], false);
});

it('should add multiple values on paste', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find(MultiValueInput).simulate('paste', {
    preventDefault: jest.fn(),
    clipboardData: {getData: () => `email1@test.com;email2@test.com email3@test.com`},
  });

  expect(props.onChange).toHaveBeenCalledWith(
    [...props.emails, 'email1@test.com', 'email2@test.com', 'email3@test.com'],
    true
  );
});

it('should remove an email', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find(MultiValueInput).prop('onRemove')('email1@hotmail.com', 0);

  expect(props.onChange).toHaveBeenCalledWith(['email2@gmail.com'], true);
});

it('should clear all emails when MultiValueInput is cleared', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find(MultiValueInput).simulate('paste', {
    preventDefault: jest.fn(),
    clipboardData: {getData: () => `email1@test.com;email2@test.com email3@test.com`},
  });

  node.find(MultiValueInput).prop('onClear')();

  expect(props.onChange).toHaveBeenCalledWith([], true);
});
