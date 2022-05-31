/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionTitle} from './getAccordionTitle';

describe('getAccordionTitle', () => {
  it('should get title for multiple instances/versions', () => {
    expect(getAccordionTitle('myProcessName', 100, 3)).toBe(
      'View 100 Instances in 3 Versions of Process myProcessName'
    );
  });

  it('should get title for single instance/version', () => {
    expect(getAccordionTitle('myProcessName', 1, 1)).toBe(
      'View 1 Instance in 1 Version of Process myProcessName'
    );
  });

  it('should get title for no instances/versions', () => {
    expect(getAccordionTitle('myProcessName', 0, 0)).toBe(
      'View 0 Instances in 0 Versions of Process myProcessName'
    );
  });
});
