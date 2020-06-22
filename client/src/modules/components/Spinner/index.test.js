/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Spinner from './index';

describe('Spinner', () => {
  let node;

  it('should match snapshot', () => {
    node = shallow(<Spinner />);
    expect(node).toMatchSnapshot();
  });

  it('should accept any passed property', () => {
    node = shallow(<Spinner foo={'bar'} />);
    expect(node.props().foo).toBe('bar');
  });
});
