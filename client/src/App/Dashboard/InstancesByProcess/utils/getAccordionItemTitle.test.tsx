/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemTitle} from './getAccordionItemTitle';

describe('getAccordionItemTitle', () => {
  it('should get title for multiple instances', () => {
    expect(getAccordionItemTitle('myProcessName', 100, 3)).toBe(
      'View 100 Instances in Version 3 of Process myProcessName'
    );
  });

  it('should get title for single instance', () => {
    expect(getAccordionItemTitle('myProcessName', 1, 2)).toBe(
      'View 1 Instance in Version 2 of Process myProcessName'
    );
  });

  it('should get title for no instances', () => {
    expect(getAccordionItemTitle('myProcessName', 0, 6)).toBe(
      'View 0 Instances in Version 6 of Process myProcessName'
    );
  });
});
