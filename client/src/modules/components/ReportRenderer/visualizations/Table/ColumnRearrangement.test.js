/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ColumnRearrangement from './ColumnRearrangement';
jest.mock('./processRawData', () => jest.fn());

it('should render child node', () => {
  const node = shallow(
    <ColumnRearrangement report={{result: {data: {}}, data: {view: {}}}}>
      some child content
    </ColumnRearrangement>
  );

  expect(node).toIncludeText('some child content');
});
