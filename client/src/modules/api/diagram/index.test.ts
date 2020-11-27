/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as wrappers from 'modules/request/wrappers';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import {fetchWorkflowXML} from './index';

describe('diagram api', () => {
  describe('fetchWorkflowXML', () => {
    it('should call get with right url and provided workflow id', async () => {
      // given
      const id = 'foo';
      const expectedURL = `/api/workflows/${id}/xml`;
      const mockXML = '<foo />';
      const successResponse = {
        text: mockResolvedAsyncFn(mockXML),
      };
      // @ts-expect-error ts-migrate(2540) FIXME: Cannot assign to 'get' because it is a read-only p... Remove this comment to see the full error message
      wrappers.get = mockResolvedAsyncFn(successResponse);

      // when
      const response = await fetchWorkflowXML(id);

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.get.mock.calls[0][0]).toBe(expectedURL);

      const data = await response.text();
      expect(data).toBe(mockXML);
    });
  });
});
