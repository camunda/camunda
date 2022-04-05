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
  concatGroupLabel,
  concatButtonTitle,
} from './service';

describe('service', () => {
  describe('concatTitle', () => {
    it('should get title for multiple instances', () => {
      expect(concatTitle('myProcessName', 100, 3)).toBe(
        'View 100 Instances in Version 3 of Process myProcessName'
      );
    });

    it('should get title for single instance', () => {
      expect(concatTitle('myProcessName', 1, 2)).toBe(
        'View 1 Instance in Version 2 of Process myProcessName'
      );
    });

    it('should get title for no instances', () => {
      expect(concatTitle('myProcessName', 0, 6)).toBe(
        'View 0 Instances in Version 6 of Process myProcessName'
      );
    });
  });

  describe('concatLabel', () => {
    it('should get label for multiple instances', () => {
      expect(concatLabel('myProcessName', 77, 3)).toBe(
        'myProcessName – 77 Instances in Version 3'
      );
    });

    it('should get label for single instance', () => {
      expect(concatLabel('myProcessName', 1, 'five')).toBe(
        'myProcessName – 1 Instance in Version five'
      );
    });

    it('should get label for no instances', () => {
      expect(concatLabel('myProcessName', 0, 'x')).toBe(
        'myProcessName – 0 Instances in Version x'
      );
    });
  });

  describe('concatGroupTitle', () => {
    it('should get title for multiple instances/versions', () => {
      expect(concatGroupTitle('myProcessName', 100, 3)).toBe(
        'View 100 Instances in 3 Versions of Process myProcessName'
      );
    });

    it('should get group title for single instance/version', () => {
      expect(concatGroupTitle('myProcessName', 1, 1)).toBe(
        'View 1 Instance in 1 Version of Process myProcessName'
      );
    });

    it('should get group title for no instances/versions', () => {
      expect(concatGroupTitle('myProcessName', 0, 0)).toBe(
        'View 0 Instances in 0 Versions of Process myProcessName'
      );
    });
  });

  describe('concatGroupLabel', () => {
    it('should get group label for multiple instances/versions', () => {
      expect(concatGroupLabel('myProcessName', 123, 5)).toBe(
        'myProcessName – 123 Instances in 5 Versions'
      );
    });

    it('should get group label for single instance/version', () => {
      expect(concatGroupLabel('myProcessName', 1, 1)).toBe(
        'myProcessName – 1 Instance in 1 Version'
      );
    });

    it('should get group label for no instances/versions', () => {
      expect(concatGroupLabel('myProcessName', 0, 0)).toBe(
        'myProcessName – 0 Instances in 0 Versions'
      );
    });
  });

  describe('concatButtonTitle', () => {
    it('should get title for multiple instances', () => {
      expect(concatButtonTitle('myProcessName', 432)).toBe(
        'Expand 432 Instances of Process myProcessName'
      );
    });

    it('should get title for single instance', () => {
      expect(concatButtonTitle('myProcessName', 1)).toBe(
        'Expand 1 Instance of Process myProcessName'
      );
    });

    it('should get title for no instances', () => {
      expect(concatButtonTitle('myProcessName', 0)).toBe(
        'Expand 0 Instances of Process myProcessName'
      );
    });
  });
});
