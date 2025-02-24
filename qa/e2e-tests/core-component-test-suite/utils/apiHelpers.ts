import {
  APIRequestContext,
  APIResponse,
  expect,
  request,
} from '@playwright/test';

export async function authAPI(
  name: string,
  password: string,
  application: string,
): Promise<void> {
  let baseURL: string;

  if (application === 'tasklist') {
    baseURL = process.env.CORE_COMPONENT_TASKLIST_URL as string;
  } else if (application === 'operate') {
    baseURL = process.env.CORE_COMPONENT_OPERATE_URL as string;
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

export function assertResponseStatus(response: APIResponse, status: number) {
  expect(response.status()).toBe(status);
}
