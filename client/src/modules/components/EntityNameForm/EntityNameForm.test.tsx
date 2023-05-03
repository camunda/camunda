/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {Input} from 'components';

import {EntityNameForm} from './EntityNameForm';

const props = {
  entity: 'Report',
  mightFail: (promise: Promise<any>, cb: ((response: any) => void) | undefined) => cb?.(promise),
  name: 'Name',
  isNew: false,
  onSave: jest.fn(),
  onCancel: jest.fn(),
  onChange: jest.fn(),
};

it('should provide name edit input', () => {
  const node = shallow(<EntityNameForm {...props} />);

  expect(node.find(Input)).toExist();
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

  node.find(Input).simulate('change', evt);

  expect(spy).toHaveBeenCalledWith(evt);
});

it('should invoke cancel', () => {
  const spy = jest.fn();
  const node = shallow(<EntityNameForm {...props} onCancel={spy} />);

  node.find('.cancel-button').simulate('click');
  expect(spy).toHaveBeenCalled();
});
