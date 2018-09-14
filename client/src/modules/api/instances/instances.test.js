import {mockResolvedAsyncFn} from 'modules/testUtils';
import * as wrappers from 'modules/request/wrappers';

import {OPERATION_TYPE} from 'modules/constants';

import {fetchWorkflowInstancesCount, retryInstances} from './instances';

import {post} from 'modules/request';

jest.mock('modules/request');

describe('instances api', () => {
  it.skip('should call post with the right url', async () => {
    // Has been detected to be broken. Ticket is created.

    //given
    const successResponse = {json: mockResolvedAsyncFn({totalCount: '123'})};
    wrappers.post = mockResolvedAsyncFn(successResponse);

    // when
    const response = await fetchWorkflowInstancesCount();

    // then
    expect(wrappers.post.mock.calls[0][0]).toBe(
      '/api/workflow-instances?firstResult=0&maxResults=1'
    );
    expect(successResponse.json).toBeCalled();
    expect(response).toEqual('123');
  });
});

describe('instance retry', () => {
  it('should call post with the right payload', async () => {
    // given
    const queries = [{id: 1, incidents: true}];

    // when
    await retryInstances(queries);

    // then
    expect(post.mock.calls[0][0]).toBe('/api/workflow-instances/operation');
    expect(post.mock.calls[0][1].operationType).toBe(
      OPERATION_TYPE.UPDATE_RETRIES
    );
    expect(post.mock.calls[0][1].queries).toBe(queries);
  });
});
