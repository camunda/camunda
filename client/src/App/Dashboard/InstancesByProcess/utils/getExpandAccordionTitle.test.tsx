/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getExpandAccordionTitle} from './getExpandAccordionTitle';

describe('getExpandAccordionTitle', () => {
  it('should get title for multiple instances', () => {
    expect(getExpandAccordionTitle('myProcessName', 432)).toBe(
      'Expand 432 Instances of Process myProcessName'
    );
  });

  it('should get title for single instance', () => {
    expect(getExpandAccordionTitle('myProcessName', 1)).toBe(
      'Expand 1 Instance of Process myProcessName'
    );
  });

  it('should get title for no instances', () => {
    expect(getExpandAccordionTitle('myProcessName', 0)).toBe(
      'Expand 0 Instances of Process myProcessName'
    );
  });
});
