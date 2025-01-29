import {APIRequestContext, expect, request, Response} from '@playwright/test';

export async function auhtAPI(name: string, password: string): Promise<void> {
  const apiRequestContext: APIRequestContext = await request.newContext();
  await apiRequestContext.post(
    '/api/login?username=' + name + '&password=' + password,
    {
      headers: {
        'Content-Type': 'application/json',
      },
    },
  );
  await apiRequestContext.storageState({path: 'utils/.auth'});
}

export async function assertResponseStatus(
  response: Response,
  status: number,
): Promise<void> {
  expect(response.status()).toBe(status);
}
