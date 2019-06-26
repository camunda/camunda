/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatButtonTitle
} from './service';

describe('service', () => {
  describe('concatTitle', () => {
    it('should get title for multiple instances', () => {
      const title = concatTitle('myWorkflow', 100, 3, 'bad error');

      expect(title).toMatchSnapshot();
    });

    it('should get title for single instance', () => {
      const title = concatTitle('myWorkflow', 1, 1, 'bad error');

      expect(title).toMatchSnapshot();
    });

    it('should get title for no instances', () => {
      const title = concatTitle('myWorkflow', 0, 2, 'bad error');

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatLabel', () => {
    it('should get label', () => {
      const title = concatLabel('myWorkflow', 2);

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatGroupTitle', () => {
    it('should get title for muliple instances/versions', () => {
      const title = concatGroupTitle(100, 'no memory left');

      expect(title).toMatchSnapshot();
    });

    it('should get group title for single instance/version', () => {
      const title = concatGroupTitle(1, 'no space left');

      expect(title).toMatchSnapshot();
    });

    it('should get group title for no instances/versions', () => {
      const title = concatGroupTitle(0, 'cannot connect');

      expect(title).toMatchSnapshot();
    });
  });

  describe('concatButtonTitle', () => {
    it('should get title for multiple instances', () => {
      const title = concatButtonTitle(100, 'no memory left');

      expect(title).toMatchSnapshot();
    });

    it('should get title for single instance', () => {
      const title = concatButtonTitle(1, 'no space left');

      expect(title).toMatchSnapshot();
    });

    it('should get title for no instances', () => {
      const title = concatButtonTitle(0, 'cannot connect');

      expect(title).toMatchSnapshot();
    });
  });
});
