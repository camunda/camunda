/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isReactElement from './isReactElement';

it('should return true if node is ReactElement', () => {
  expect(isReactElement(<div />)).toBe(true);
});

it('should return false if node is not ReactElement', () => {
  expect(isReactElement('string')).toBe(false);
});
