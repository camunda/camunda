/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type RequestError = {
  variant: 'network-error' | 'failed-response';
  response: Response | null;
  networkError: unknown | null;
};

async function request(
  input: RequestInfo | URL,
  init?: RequestInit | undefined,
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
  try {
    const response = await fetch(input, init);

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
        networkError: null,
        variant: 'failed-response',
      },
    };
  } catch (error) {
    return {
      response: null,
      error: {
        response: null,
        networkError: error,
        variant: 'network-error',
      },
    };
  }
}

export {request};
export type {RequestError};
