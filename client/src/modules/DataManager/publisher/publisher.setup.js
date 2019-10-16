/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const MOCK_TOPICS = {
  FETCH_STATE_FOO: 'FETCH_STATE_FOO',
  FETCH_STATE_BAR: 'FETCH_STATE_BAR'
};

export const topicFoo = MOCK_TOPICS.FETCH_STATE_FOO;
export const topicBar = MOCK_TOPICS.FETCH_STATE_BAR;

export const mockApiData = {
  success: {data: ['someData', 'someMoreData'], error: null},
  error: {data: [], error: 'fetchError'}
};
