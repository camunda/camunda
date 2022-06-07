/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemTitle} from './getAccordionItemTitle';

describe('service', () => {
  describe('getAccordionItemTitle', () => {
    it('should get title for multiple instances', () => {
      expect(getAccordionItemTitle('myProcess', 100, 3, 'bad error')).toBe(
        'View 100 Instances with error bad error in version 3 of Process myProcess'
      );
    });

    it('should get title for single instance', () => {
      expect(getAccordionItemTitle('myProcess', 1, 1, 'bad error')).toBe(
        'View 1 Instance with error bad error in version 1 of Process myProcess'
      );
    });

    it('should get title for no instances', () => {
      expect(getAccordionItemTitle('myProcess', 0, 2, 'bad error')).toBe(
        'View 0 Instances with error bad error in version 2 of Process myProcess'
      );
    });
  });
});
