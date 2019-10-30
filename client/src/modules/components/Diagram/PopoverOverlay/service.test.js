/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getBreadcrumbs} from './service';
import {
  defaultProps,
  multiInstanceBodyProps,
  multiInstanceChildProps
} from './service.setup';

describe('PopupOverlay.service', () => {
  describe('getBreadcrumbs', () => {
    it('should return default breadcrumbs', () => {
      expect(getBreadcrumbs(defaultProps)).toMatchSnapshot();
    });

    it('should return multi instance body breadcrumbs', () => {
      expect(getBreadcrumbs(multiInstanceBodyProps)).toMatchSnapshot();
    });

    it('should return multi instance child breadcrumbs', () => {
      expect(getBreadcrumbs(multiInstanceChildProps)).toMatchSnapshot();
    });
  });
});
