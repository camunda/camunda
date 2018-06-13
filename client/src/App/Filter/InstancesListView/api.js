import {post} from 'request';

export async function getData() {
  const response = await post('/workflow-instances', {});

  return await response.json();
}
