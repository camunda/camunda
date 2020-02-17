/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Skeleton from './Skeleton';

describe('Tree Skeleton', () => {
  it('should render rows according to properties', () => {
    const node = mount(<Skeleton rowsToDisplay={10} />);

    // x rows
    expect(node.find('Row')).toHaveLength(10);
  });
});
