import * as wrappers from 'modules/request/wrappers';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import {workflowXML} from './diagram';

describe('diagram api', () => {
  describe('workflowXML', () => {
    it('should call get with right url and provided workflow id', async () => {
      // given
      const id = 'foo';
      const expectedURL = `/api/workflows/${id}/xml`;
      const mockXML = '<foo />';
      const successResponse = {
        text: mockResolvedAsyncFn(mockXML)
      };
      wrappers.get = mockResolvedAsyncFn(successResponse);

      // when
      const response = await workflowXML(id);

      // then
      expect(wrappers.get.mock.calls[0][0]).toBe(expectedURL);
      expect(successResponse.text).toHaveBeenCalledTimes(1);
      expect(response).toBe(mockXML);
    });
  });
});
