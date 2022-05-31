/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionLabel} from './getAccordionLabel';

describe('getAccordionLabel', () => {
  it('should get accordion label for multiple instances/versions', () => {
    expect(getAccordionLabel('myProcessName', 123, 5)).toBe(
      'myProcessName – 123 Instances in 5 Versions'
    );
  });

  it('should get accordion label for single instance/version', () => {
    expect(getAccordionLabel('myProcessName', 1, 1)).toBe(
      'myProcessName – 1 Instance in 1 Version'
    );
  });

  it('should get accordion label for no instances/versions', () => {
    expect(getAccordionLabel('myProcessName', 0, 0)).toBe(
      'myProcessName – 0 Instances in 0 Versions'
    );
  });
});
