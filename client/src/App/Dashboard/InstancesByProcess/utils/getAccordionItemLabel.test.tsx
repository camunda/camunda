/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemLabel} from './getAccordionItemLabel';

describe('getAccordionItemLabel', () => {
  it('should get label for multiple instances', () => {
    expect(getAccordionItemLabel('myProcessName', 77, 3)).toBe(
      'myProcessName – 77 Instances in Version 3'
    );
  });

  it('should get label for single instance', () => {
    expect(getAccordionItemLabel('myProcessName', 1, 5)).toBe(
      'myProcessName – 1 Instance in Version 5'
    );
  });

  it('should get label for no instances', () => {
    expect(getAccordionItemLabel('myProcessName', 0, 2)).toBe(
      'myProcessName – 0 Instances in Version 2'
    );
  });
});
