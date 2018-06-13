import {post} from 'modules/request';

export async function getData() {
  const response = await post('/api/workflow-instances', {});

  return await response.json();
}
