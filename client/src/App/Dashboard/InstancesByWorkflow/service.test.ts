/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatGroupLabel,
  concatButtonTitle,
  concatUrl,
} from './service';

describe('service', () => {
  describe('concatTitle', () => {
    it('should get title for multiple instances', () => {
      expect(concatTitle('myProcessName', 100, 3)).toBe(
        'View 100 Instances in Version 3 of Workflow myProcessName'
      );
    });

    it('should get title for single instance', () => {
      expect(concatTitle('myProcessName', 1, 2)).toBe(
        'View 1 Instance in Version 2 of Workflow myProcessName'
      );
    });

    it('should get title for no instances', () => {
      expect(concatTitle('myProcessName', 0, 6)).toBe(
        'View 0 Instances in Version 6 of Workflow myProcessName'
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
        'View 100 Instances in 3 Versions of Workflow myProcessName'
      );
    });

    it('should get group title for single instance/version', () => {
      expect(concatGroupTitle('myProcessName', 1, 1)).toBe(
        'View 1 Instance in 1 Version of Workflow myProcessName'
      );
    });

    it('should get group title for no instances/versions', () => {
      expect(concatGroupTitle('myProcessName', 0, 0)).toBe(
        'View 0 Instances in 0 Versions of Workflow myProcessName'
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
        'Expand 432 Instances of Workflow myProcessName'
      );
    });

    it('should get title for single instance', () => {
      expect(concatButtonTitle('myProcessName', 1)).toBe(
        'Expand 1 Instance of Workflow myProcessName'
      );
    });

    it('should get title for no instances', () => {
      expect(concatButtonTitle('myProcessName', 0)).toBe(
        'Expand 0 Instances of Workflow myProcessName'
      );
    });
  });

  describe('concatUrl', () => {
    it('should get url - single version, finished instances', () => {
      expect(
        concatUrl({
          bpmnProcessId: 'Process_1',
          versions: [{version: 1}],
          hasFinishedInstances: true,
          name: 'Process_1',
        })
      ).toBe(
        '/instances?filter={"workflow":"Process_1","version":"1","incidents":true,"active":true,"completed":true,"canceled":true}&name="Process_1"'
      );
    });

    it('should get url - all versions, no finished instances', () => {
      expect(
        concatUrl({
          bpmnProcessId: 'Process_2',
          versions: [{version: 1}, {version: 2}],
          name: 'Process_2',
        })
      ).toBe(
        '/instances?filter={"workflow":"Process_2","version":"all","incidents":true,"active":true}&name="Process_2"'
      );
    });
  });
});
