/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getExpandAccordionTitle} from './getExpandAccordionTitle';

describe('service', () => {
  describe('getExpandAccordionTitle', () => {
    it('should get title for multiple instances', () => {
      expect(getExpandAccordionTitle(100, 'no memory left')).toBe(
        'Expand 100 Instances with error no memory left'
      );
    });

    it('should get title for single instance', () => {
      expect(getExpandAccordionTitle(1, 'no space left')).toBe(
        'Expand 1 Instance with error no space left'
      );
    });

    it('should get title for no instances', () => {
      expect(getExpandAccordionTitle(0, 'cannot connect')).toBe(
        'Expand 0 Instances with error cannot connect'
      );
    });
  });
});
