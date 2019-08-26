/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {Labeled} from 'components';

import LabeledInput from './LabeledInput';

jest.mock('components', () => ({
  Labeled: props => (
    <div>
      <label id={props.id}>{props.label}</label>
      {props.children}
    </div>
  ),
  Input: props => <input {...props} />
}));

it('should create a label with the provided id', () => {
  const node = mount(<LabeledInput id="someId" />);

  expect(node.find('Labeled')).toHaveProp('id', 'someId');
});

it('should include the child content', () => {
  const node = mount(<LabeledInput>some child content</LabeledInput>);

  expect(node).toIncludeText('some child content');
});

it('should can be disabled', () => {
  const node = mount(<LabeledInput disabled>some child content</LabeledInput>);

  expect(node.find(Labeled)).toHaveProp('disabled', true);
});
