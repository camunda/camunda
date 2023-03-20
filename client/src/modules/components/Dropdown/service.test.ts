/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {findLetterOption} from './service';

const option1 = {textContent: 'foo'} as unknown as HTMLElement;
const option2 = {textContent: 'bar'} as unknown as HTMLElement;

it('should find the next possible option that matches a letter', () => {
  expect(findLetterOption([option1, option2], 'b', 0)).toEqual({
    textContent: 'bar',
  });
});

it('should search before the start index if no element is found after it', () => {
  expect(findLetterOption([option1, option2], 'f', 1)).toEqual({
    textContent: 'foo',
  });
});

it('should search before the start index if no element is found after it', () => {
  expect(findLetterOption([option1, option2], 'c', 0)).toEqual(undefined);
});
