/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getFlowNodeNames} from './dataLoaders';
import {post} from 'request';

jest.mock('request', () => ({
  post: jest.fn(),
}));

post.mockReturnValueOnce({json: () => ({flowNodeNames: {a: 'foo'}})});

it('should return an empty object if process definition key or version is invalid', async () => {
  const flowNodeNames = await getFlowNodeNames('', '');

  expect(flowNodeNames).toEqual({});
  expect(post).not.toHaveBeenCalled();
});

it('should return flow node names if key and version are valid', async () => {
  const flowNodeNames = await getFlowNodeNames('aKey', '1');

  expect(flowNodeNames).toEqual({a: 'foo'});
  expect(post).toHaveBeenCalled();
});
