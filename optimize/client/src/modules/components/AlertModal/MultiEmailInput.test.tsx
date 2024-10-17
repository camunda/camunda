/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChangeEvent, KeyboardEvent} from 'react';
import {shallow} from 'enzyme';
import {MultiValueInput} from '@camunda/camunda-optimize-composite-components';

import MultiEmailInput from './MultiEmailInput';

const props = {
  emails: ['email1@hotmail.com', 'email2@gmail.com'],
  onChange: jest.fn(),
  titleText: 'test',
};

beforeEach(() => props.onChange.mockClear());

it('should render email tags', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  const tags = node.find(MultiValueInput).dive().find('RemovableTag');
  expect(tags.at(0).prop('title')).toBe('email1@hotmail.com');
  expect(tags.at(1).prop('title')).toBe('email2@gmail.com');
});

it('should add an email', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  node.find(MultiValueInput).prop('onChange')?.({
    target: {value: 'test@test.com'},
  } as unknown as ChangeEvent<HTMLInputElement>);
  node.find(MultiValueInput).prop('onKeyDown')?.({
    key: ',',
    preventDefault: jest.fn(),
  } as unknown as KeyboardEvent<HTMLInputElement>);

  expect(props.onChange).toHaveBeenCalledWith([...props.emails, 'test@test.com'], true);

  props.onChange.mockClear();

  node.find(MultiValueInput).prop('onChange')?.({
    target: {value: 'invalid'},
  } as unknown as ChangeEvent<HTMLInputElement>);
  node.find(MultiValueInput).prop('onKeyDown')?.({
    key: ',',
    preventDefault: jest.fn(),
  } as unknown as KeyboardEvent<HTMLInputElement>);

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

it('should remove email when remove button is clicked on removable tag', () => {
  const node = shallow(<MultiEmailInput {...props} />);

  props.onChange.mockClear();
  node.find(MultiValueInput).dive().find('RemovableTag').at(0).prop<jest.Mock>('onRemove')();
  expect(props.onChange).toHaveBeenCalledWith(['email2@gmail.com'], true);
});
