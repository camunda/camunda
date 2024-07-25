/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
