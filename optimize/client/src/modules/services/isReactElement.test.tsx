/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import isReactElement from './isReactElement';

it('should return true if node is ReactElement', () => {
  expect(isReactElement(<div />)).toBe(true);
});

it('should return false if node is not ReactElement', () => {
  expect(isReactElement('string')).toBe(false);
});
