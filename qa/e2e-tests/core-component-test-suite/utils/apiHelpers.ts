import {
  APIRequestContext,
  expect,
  request,
  APIResponse,
} from '@playwright/test';

export async function auhtAPI(name: string, password: string): Promise<void> {
  const apiRequestContext: APIRequestContext = await request.newContext();
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

export function assertResponseStatus(response: APIResponse, status: number) {
  expect(response.status()).toBe(status);
}
