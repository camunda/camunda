/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {TextInput} from '@carbon/react';

import EntityNameForm from './EntityNameForm';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockReturnValue({
    mightFail: (promise: Promise<unknown>, cb: ((response: unknown) => unknown) | undefined) =>
      cb?.(promise),
  }),
}));

const props = {
  entity: 'Report',
  name: 'Name',
  isNew: false,
  onSave: jest.fn(),
  onCancel: jest.fn(),
  onChange: jest.fn(),
};

it('should provide name edit input', () => {
  const node = shallow(<EntityNameForm {...props} />);

  expect(node.find(TextInput)).toExist();
});

it('should provide a link to view mode', () => {
  const node = shallow(<EntityNameForm {...props} />);

  expect(node.find('.save-button')).toExist();
  expect(node.find('.cancel-button')).toExist();
});

it('should invoke save on save button click', () => {
  const spy = jest.fn();
  const node = shallow(<EntityNameForm {...props} name="" onSave={spy} />);

  node.find('.save-button').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should disable save button if report name is empty', () => {
  const node = shallow(<EntityNameForm {...props} name="" />);

  expect(node.find('.save-button')).toBeDisabled();
});

it('should call change function on input change', () => {
  const spy = jest.fn();
  const node = shallow(<EntityNameForm {...props} name="test name" onChange={spy} />);

  const evt = {target: {value: 'asdf'}};

  node.find(TextInput).simulate('change', evt);

  expect(spy).toHaveBeenCalledWith(evt);
});

it('should invoke cancel', () => {
  const spy = jest.fn();
  const node = shallow(<EntityNameForm {...props} onCancel={spy} />);

  node.find('.cancel-button').simulate('click');
  expect(spy).toHaveBeenCalled();
});
