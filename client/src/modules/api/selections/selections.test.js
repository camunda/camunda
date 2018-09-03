import {mockResolvedAsyncFn} from 'modules/testUtils';
import * as wrappers from 'modules/request/wrappers';

import {batchRetry} from './selections';

describe('selections api', () => {
  it('should call post with the right payload', async () => {
    //given
    const successResponse = {json: mockResolvedAsyncFn({})};
    wrappers.post = mockResolvedAsyncFn(successResponse);

    // when
    const response = await batchRetry();

    // then
    expect(successResponse.json).toBeCalled();
    expect(response).toEqual({});
  });
});
