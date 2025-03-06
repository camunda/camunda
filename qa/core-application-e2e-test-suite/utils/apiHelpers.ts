/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, request} from '@playwright/test';

export async function authAPI(
  name: string,
  password: string,
  application: string,
): Promise<void> {
  let baseURL: string;

  if (application === 'tasklist') {
    baseURL = process.env.CORE_APPLICATION_TASKLIST_URL as string;
  } else if (application === 'operate') {
    baseURL = process.env.CORE_APPLICATION_OPERATE_URL as string;
  } else {
    throw new Error(`Unsupported application: ${application}`);
  }

  const apiRequestContext: APIRequestContext = await request.newContext({
    baseURL,
  });
  await apiRequestContext.post('/api/login', {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    form: {
      username: name,
      password: password,
    },
  });
  await apiRequestContext.storageState({path: 'utils/.auth'});
}
