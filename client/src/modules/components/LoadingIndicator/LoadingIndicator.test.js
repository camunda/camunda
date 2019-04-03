/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import LoadingIndicator from './LoadingIndicator';

it('should render without crashing', () => {
  mount(<LoadingIndicator />);
});

it('should create a label with the provided id', () => {
  const node = mount(<LoadingIndicator id="someId" />);

  expect(node.find('.sk-circle')).toHaveProp('id', 'someId');
});

it('should be possible to get a smaller version', () => {
  const node = mount(<LoadingIndicator small />);

  expect(node.find('.sk-circle')).toHaveClassName('small');
});
