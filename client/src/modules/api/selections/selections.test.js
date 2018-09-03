import {post} from 'modules/request';

import {batchRetry} from './selections';

jest.mock('modules/request');

describe('selections api', () => {
  it('should call post with the right payload', async () => {
    // given
    const queries = [{id: 1, incidents: true}];

    // when
    await batchRetry(queries);

    // then
    expect(post.mock.calls[0][0]).toBe('/api/workflow-instances/operation');
    expect(post.mock.calls[0][1].operationType).toBe('UPDATE_RETRIES');
    expect(post.mock.calls[0][1].queries).toBe(queries);
  });
});
