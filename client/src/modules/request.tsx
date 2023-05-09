/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mergePathname} from 'modules/utils/mergePathname';

type RequestError = {
  variant: 'network-error' | 'failed-response';
  response: Response | null;
  error: unknown | null;
};

async function request(
  input: string,
  init?: RequestInit,
): Promise<
  | {
      response: Response;
      error: null;
    }
  | {
      response: null;
      error: RequestError;
    }
> {
  const BASENAME = window.clientConfig?.contextPath ?? '/';
  try {
    const response = await fetch(mergePathname(BASENAME, input), {
      ...init,
      credentials: 'include',
      mode: 'cors',
    });

    if (response.ok) {
      return {
        response,
        error: null,
      };
    }

    return {
      response: null,
      error: {
        response,
        error: null,
        variant: 'failed-response',
      },
    };
  } catch (error) {
    return {
      response: null,
      error: {
        response: null,
        error,
        variant: 'network-error',
      },
    };
  }
}

export {request};
