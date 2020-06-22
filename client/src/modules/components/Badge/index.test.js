/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Badge from './index';

describe('Badge', () => {
  it('should contain passed number', () => {
    const node = shallow(<Badge>123</Badge>);
    expect(node.contains('123')).toBe(true);
    expect(node).toMatchSnapshot();
  });
});
