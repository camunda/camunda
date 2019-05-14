/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  getTitle,
  getGroupTitle,
  getLabel,
  getGroupLabel,
  getButtonTitle
} from './service';

describe('service', () => {
  describe('getTitle', () => {
    it('should get title for multiple instances', () => {
      const title = getTitle('myProcessName', 100, 3);

      expect(title).toMatchSnapshot();
    });

    it('should get title for single instance', () => {
      const title = getTitle('myProcessName', 1, 2);

      expect(title).toMatchSnapshot();
    });

    it('should get title for no instances', () => {
      const title = getTitle('myProcessName', 0, 6);

      expect(title).toMatchSnapshot();
    });
  });

  describe('getLabel', () => {
    it('should get label for multiple instances', () => {
      const title = getLabel('myProcessName', 77, 3);

      expect(title).toMatchSnapshot();
    });

    it('should get label for single instance', () => {
      const title = getLabel('myProcessName', 1, 'five');

      expect(title).toMatchSnapshot();
    });

    it('should get label for no instances', () => {
      const title = getLabel('myProcessName', 0, 'x');

      expect(title).toMatchSnapshot();
    });
  });

  describe('getGroupTitle', () => {
    it('should get title for muliple instances/versions', () => {
      const title = getGroupTitle('myProcessName', 100, 3);

      expect(title).toMatchSnapshot();
    });

    it('should get group title for single instance/version', () => {
      const title = getGroupTitle('myProcessName', 1, 1);

      expect(title).toMatchSnapshot();
    });

    it('should get group title for no instances/versions', () => {
      const title = getGroupTitle('myProcessName', 0, 0);

      expect(title).toMatchSnapshot();
    });
  });

  describe('getGroupLabel', () => {
    it('should get group label for multiple instances/versions', () => {
      const title = getGroupLabel('myProcessName', 123, 5);

      expect(title).toMatchSnapshot();
    });

    it('should get group label for single instance/version', () => {
      const title = getGroupLabel('myProcessName', 1, 1);

      expect(title).toMatchSnapshot();
    });

    it('should get group label for no instances/versions', () => {
      const title = getGroupLabel('myProcessName', 0, 0);

      expect(title).toMatchSnapshot();
    });
  });

  describe('getButtonTitle', () => {
    it('should get title for multiple instances', () => {
      const title = getButtonTitle('myProcessName', 432);

      expect(title).toMatchSnapshot();
    });

    it('should get title for single instance', () => {
      const title = getButtonTitle('myProcessName', 1);

      expect(title).toMatchSnapshot();
    });

    it('should get title for no instances', () => {
      const title = getButtonTitle('myProcessName', 0);

      expect(title).toMatchSnapshot();
    });
  });
});
