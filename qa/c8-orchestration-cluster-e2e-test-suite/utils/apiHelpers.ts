/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, request} from '@playwright/test';

export async function authAPI(name: string, password: string): Promise<void> {
  let baseURL: string;
  let authFilePath: string;

  baseURL = process.env.CORE_APPLICATION_URL as string;
  authFilePath = 'utils/.auth';

  const apiRequestContext: APIRequestContext = await request.newContext({
    baseURL,
  });
  await apiRequestContext.post('/login', {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    form: {
      username: name,
      password: password,
    },
  });
  await apiRequestContext.storageState({path: authFilePath});
}
