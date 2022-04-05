/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatButtonTitle,
} from './service';

describe('service', () => {
  describe('concatTitle', () => {
    it('should get title for multiple instances', () => {
      expect(concatTitle('myProcess', 100, 3, 'bad error')).toBe(
        'View 100 Instances with error bad error in version 3 of Process myProcess'
      );
    });

    it('should get title for single instance', () => {
      expect(concatTitle('myProcess', 1, 1, 'bad error')).toBe(
        'View 1 Instance with error bad error in version 1 of Process myProcess'
      );
    });

    it('should get title for no instances', () => {
      expect(concatTitle('myProcess', 0, 2, 'bad error')).toBe(
        'View 0 Instances with error bad error in version 2 of Process myProcess'
      );
    });
  });

  describe('concatLabel', () => {
    it('should get label', () => {
      expect(concatLabel('myProcess', 2)).toBe('myProcess – Version 2');
    });
  });

  describe('concatGroupTitle', () => {
    it('should get title for multiple instances/versions', () => {
      expect(concatGroupTitle(100, 'no memory left')).toBe(
        'View 100 Instances with error no memory left'
      );
    });

    it('should get group title for single instance/version', () => {
      expect(concatGroupTitle(1, 'no space left')).toBe(
        'View 1 Instance with error no space left'
      );
    });

    it('should get group title for no instances/versions', () => {
      expect(concatGroupTitle(0, 'cannot connect')).toBe(
        'View 0 Instances with error cannot connect'
      );
    });
  });

  describe('concatButtonTitle', () => {
    it('should get title for multiple instances', () => {
      expect(concatButtonTitle(100, 'no memory left')).toBe(
        'Expand 100 Instances with error no memory left'
      );
    });

    it('should get title for single instance', () => {
      expect(concatButtonTitle(1, 'no space left')).toBe(
        'Expand 1 Instance with error no space left'
      );
    });

    it('should get title for no instances', () => {
      expect(concatButtonTitle(0, 'cannot connect')).toBe(
        'Expand 0 Instances with error cannot connect'
      );
    });
  });
});
