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
  concatUrl
} from './service';

describe('service', () => {
  describe('concatTitle', () => {
    it('should get title for multiple instances', () => {
      const title = concatTitle('myProcessName', 100, 3);

      expect(title).toMatchSnapshot();
    });

    it('should get title for single instance', () => {
      const title = concatTitle('myProcessName', 1, 2);

      expect(title).toMatchSnapshot();
    });

    it('should get title for no instances', () => {
      const title = concatTitle('myProcessName', 0, 6);

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatLabel', () => {
    it('should get label for multiple instances', () => {
      const title = concatLabel('myProcessName', 77, 3);

      expect(title).toMatchSnapshot();
    });

    it('should get label for single instance', () => {
      const title = concatLabel('myProcessName', 1, 'five');

      expect(title).toMatchSnapshot();
    });

    it('should get label for no instances', () => {
      const title = concatLabel('myProcessName', 0, 'x');

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatGroupTitle', () => {
    it('should get title for muliple instances/versions', () => {
      const title = concatGroupTitle('myProcessName', 100, 3);

      expect(title).toMatchSnapshot();
    });

    it('should get group title for single instance/version', () => {
      const title = concatGroupTitle('myProcessName', 1, 1);

      expect(title).toMatchSnapshot();
    });

    it('should get group title for no instances/versions', () => {
      const title = concatGroupTitle('myProcessName', 0, 0);

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatGroupLabel', () => {
    it('should get group label for multiple instances/versions', () => {
      const title = concatGroupLabel('myProcessName', 123, 5);

      expect(title).toMatchSnapshot();
    });

    it('should get group label for single instance/version', () => {
      const title = concatGroupLabel('myProcessName', 1, 1);

      expect(title).toMatchSnapshot();
    });

    it('should get group label for no instances/versions', () => {
      const title = concatGroupLabel('myProcessName', 0, 0);

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatButtonTitle', () => {
    it('should get title for multiple instances', () => {
      const title = concatButtonTitle('myProcessName', 432);

      expect(title).toMatchSnapshot();
    });

    it('should get title for single instance', () => {
      const title = concatButtonTitle('myProcessName', 1);

      expect(title).toMatchSnapshot();
    });

    it('should get title for no instances', () => {
      const title = concatButtonTitle('myProcessName', 0);

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatUrl', () => {
    it('should get url - single version, finished instances', () => {
      const url = concatUrl({
        bpmnProcessId: 'Process_1',
        versions: [{version: 1}],
        hasFinishedInstances: true,
        name: 'Process_1'
      });

      expect(url).toMatchSnapshot();
    });

    it('should get url - all versions, no finished instances', () => {
      const url = concatUrl({
        bpmnProcessId: 'Process_2',
        versions: [{version: 1}, {version: 2}],
        name: 'Process_2'
      });

      expect(url).toMatchSnapshot();
    });
  });
});
