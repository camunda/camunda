/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext, request} from '@playwright/test';
import {sleep} from './sleep';

export async function authAPI(
  name: string,
  password: string,
  application: string,
): Promise<void> {
  let baseURL: string;
  let authFilePath: string; // Define separate storage path for each app
  if (application === 'tasklist') {
    baseURL = process.env.CORE_APPLICATION_TASKLIST_URL as string;
    authFilePath = 'utils/.auth_tasklist'; // Tasklist-specific auth file
  } else if (application === 'operate') {
    baseURL = process.env.CORE_APPLICATION_OPERATE_URL as string;
    authFilePath = 'utils/.auth_operate'; // Operate-specific auth file
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
  // Save authentication session in the app-specific file
  await apiRequestContext.storageState({path: authFilePath});
}

export async function pollAPI<T>(
  request: APIRequestContext,
  endpoint: string,
  condition: (response: T) => boolean,
  maxRetries = 3,
  retryInterval = 5000,
): Promise<T> {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    const response = await request.post(endpoint);
    const responseData: T = await response.json();
    if (condition(responseData)) {
      console.log(`Condition met on attempt ${attempt + 1}`);
      return responseData;
    }
    console.log(
      `Condition not met on attempt ${attempt + 1}, retrying in ${retryInterval}ms...`,
    );
    await sleep(retryInterval);
  }
  throw new Error(`Condition not met after ${maxRetries} retries`);
}
