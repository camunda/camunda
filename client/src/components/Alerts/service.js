import {get} from 'request';

export async function loadReports() {
  const response = await get('/api/report');

  return await response.json();
}

export async function emailNotificationIsEnabled() {
  const response = await get('/api/alert/email/isEnabled');

  return (await response.json()).enabled;
}
