import {mockResolvedAsyncFn} from 'modules/testUtils';
import * as wrappers from 'modules/request/wrappers';

import {fetchWorkflowInstancesCount} from './instances';

describe('dashboard api', () => {
  it('should call post with the right url', async () => {
    //given
    const successResponse = {json: mockResolvedAsyncFn({count: '123'})};
    wrappers.post = mockResolvedAsyncFn(successResponse);

    // when
    const response = await fetchWorkflowInstancesCount();

    // then
    expect(wrappers.post.mock.calls[0][0]).toBe(
      '/api/workflow-instances/count'
    );
    expect(successResponse.json).toBeCalled();
    expect(response).toEqual('123');
  });
});
