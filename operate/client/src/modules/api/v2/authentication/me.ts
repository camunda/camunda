/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import {
  type CurrentUser,
  endpoints,
} from '@camunda/camunda-api-zod-schemas/8.10';

/**
 * Tells if a current user response is for a caller without a session. An unprotected API answers
 * such a caller with an anonymous user instead of a 401, because no credentials can authenticate it
 * there.
 */
async function isAnonymousCurrentUser(response: Response): Promise<boolean> {
  try {
    const body: unknown = await response.json();
    return (
      typeof body === 'object' &&
      body !== null &&
      'anonymous' in body &&
      body.anonymous === true
    );
  } catch {
    return false;
  }
}

const getMe = async () => {
  return requestWithThrow<CurrentUser>(
    {
      url: endpoints.getCurrentUser.getUrl(),
      method: endpoints.getCurrentUser.method,
    },
    {isUnauthenticated: isAnonymousCurrentUser},
  );
};

export {getMe, isAnonymousCurrentUser};
