import {get} from 'request';

export async function emailNotificationIsEnabled() {
  const response = await get('/api/alert/email/isEnabled');

  return (await response.json()).enabled;
}
