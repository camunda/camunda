/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import Textarea from './Textarea';

describe('Textarea', () => {
  it('should render default textarea', () => {
    const node = mount(<Textarea />);

    expect(node.find('textarea').exists()).toBe(true);
  });

  it('should render autosize textarea', () => {
    const node = mount(<Textarea hasAutoSize />);

    expect(node.find('textarea').exists()).toBe(true);
  });
});
