/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {expect} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {defaultAssertionOptions, generateUniqueId} from '../constants';
import {validateResponse} from '../../json-body-assertions';

export type GlobalTaskListenerBody = {
  id: string;
  type: string;
  eventTypes: string[];
  retries?: number;
  afterNonGlobal?: boolean;
  priority?: number;
};

export async function createGlobalTaskListener(
  request: APIRequestContext,
  overrides?: Partial<GlobalTaskListenerBody>,
): Promise<GlobalTaskListenerBody> {
  const id = overrides?.id ?? `test-gl-${generateUniqueId()}`;
  const body: GlobalTaskListenerBody = {
    id,
    type: `io.camunda.test.listener.${id}`,
    eventTypes: ['creating', 'completing'],
    ...overrides,
  };

  await expect(async () => {
    const res = await request.post(buildUrl('/global-task-listeners'), {
      headers: jsonHeaders(),
      data: body,
    });
    await assertStatusCode(res, 201);
    await validateResponse(
      {path: '/global-task-listeners', method: 'POST', status: '201'},
      res,
    );
  }).toPass(defaultAssertionOptions);

  return body;
}
