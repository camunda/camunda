/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createInstance} from 'modules/testUtils';

export const MOCK_TOPICS = {
  FETCH_STATE_FOO: 'FETCH_STATE_FOO',
  FETCH_STATE_BAR: 'FETCH_STATE_BAR'
};

export const mockWorkflowInstance = createInstance();
export const mockParams = {};
export const customTopic = 'CUSTOM_TOPIC';
export const mockStaticContent = {data: 'someData'};
export const mockScopeId = '1234';
