/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import BooleanInput from './BooleanInput';

import {mount} from 'enzyme';

jest.mock('components', () => {
  return {
    Button: props => <button {...props}>{props.children}</button>,
    ButtonGroup: props => <div {...props}>{props.children}</div>
  };
});

const props = {
  filter: BooleanInput.defaultFilter,
  setValid: jest.fn()
};

it('should assume variable value true per default', () => {
  expect(BooleanInput.defaultFilter.value).toEqual(true);
});

it('should show true and false operator fields', () => {
  const node = mount(<BooleanInput {...props} />);

  expect(node.find('button').at(0)).toIncludeText('true');
  expect(node.find('button').at(1)).toIncludeText('false');
});

it('should set the value when clicking on the operator fields', () => {
  const spy = jest.fn();
  const node = mount(<BooleanInput {...props} changeFilter={spy} />);

  node
    .find('button')
    .at(1)
    .simulate('click');

  expect(spy).toHaveBeenCalledWith({value: false});
});
