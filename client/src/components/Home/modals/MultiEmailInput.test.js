/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Input, Button} from 'components';

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

  const input = node.find(Input);
  input.simulate('keyDown', {
    key: ',',
    target: {value: 'test@test.com'},
    preventDefault: jest.fn(),
  });

  expect(props.onChange).toHaveBeenCalledWith([...props.emails, 'test@test.com'], true);

  props.onChange.mockClear();

  input.simulate('blur', {target: {value: 'invalid'}});

  expect(props.onChange).toHaveBeenCalledWith([...props.emails, 'invalid'], false);
});

it('should add multiple values on paste', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find(Input).simulate('paste', {
    preventDefault: jest.fn(),
    clipboardData: {getData: () => `email1@test.com;email2@test.com email3@test.com`},
  });

  expect(props.onChange).toHaveBeenCalledWith(
    [...props.emails, 'email1@test.com', 'email2@test.com', 'email3@test.com'],
    true
  );
});

it('should remove an email when clicking the x button of tag', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find('.tag').at(0).find(Button).simulate('click');

  expect(props.onChange).toHaveBeenCalledWith(['email2@gmail.com'], true);

  props.onChange.mockClear();

  node.find(Input).simulate('keyDown', {
    key: 'Backspace',
    target: {value: ''},
  });

  expect(props.onChange).toHaveBeenCalledWith(['email1@hotmail.com'], true);
});
