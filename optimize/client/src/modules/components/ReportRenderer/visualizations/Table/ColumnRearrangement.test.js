/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ColumnRearrangement from './ColumnRearrangement';

it('should render child node only if it is disabled', () => {
  const node = shallow(
    <ColumnRearrangement enabled={false}>some child content</ColumnRearrangement>
  );

  expect(node).not.toHaveClassName('.ColumnRearrangement');
  expect(node).toIncludeText('some child content');
});

it('should render columnRearrangement wrapper if it is enabled', () => {
  const node = shallow(
    <ColumnRearrangement enabled={true}>some child content</ColumnRearrangement>
  );

  expect(node).toHaveClassName('.ColumnRearrangement');
});
