/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getNonOverflowingValues} from './service';

it('should return new alignment and position if no space at the edges of the screen', () => {
  jest.spyOn(document.body, 'clientHeight', 'get').mockReturnValueOnce(100);
  jest.spyOn(document.body, 'clientWidth', 'get').mockReturnValueOnce(100);
  jest
    .spyOn(window, 'getComputedStyle')
    .mockImplementationOnce(
      () => ({getPropertyValue: () => '7px'} as unknown as CSSStyleDeclaration)
    );

  const tooltip = {
    getBoundingClientRect: () => ({width: 50, height: 50}),
  } as unknown as HTMLElement;
  const hoverElement = {
    getBoundingClientRect: () => ({x: 10, y: 10, width: 10, top: 10, bottom: 20}),
  } as unknown as HTMLElement;

  expect(getNonOverflowingValues(tooltip, hoverElement, 'right', 'top')).toEqual({
    newAlign: 'left',
    newPosition: 'bottom',
    left: 10,
    top: 20,
    width: 50,
  });
});

it('should keep alignment and position if there is a space for the tooltip at the edges of the screen', () => {
  jest.spyOn(document.body, 'clientHeight', 'get').mockReturnValueOnce(100);
  jest.spyOn(document.body, 'clientWidth', 'get').mockReturnValueOnce(100);
  jest
    .spyOn(window, 'getComputedStyle')
    .mockImplementationOnce(
      () => ({getPropertyValue: () => '5px'} as unknown as CSSStyleDeclaration)
    );

  const tooltip = {
    getBoundingClientRect: () => ({width: 50, height: 50}),
  } as unknown as HTMLElement;
  const hoverElement = {
    getBoundingClientRect: () => ({x: 60, y: 60, width: 10, top: 60, bottom: 70}),
  } as unknown as HTMLElement;

  expect(getNonOverflowingValues(tooltip, hoverElement, 'right', 'top')).toEqual({
    newAlign: 'right',
    newPosition: 'top',
    left: 70, // x + width
    top: 60,
    width: 50,
  });
});
