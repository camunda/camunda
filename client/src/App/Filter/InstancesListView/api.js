import {post} from 'modules/request';

export async function getData(filter) {
  const response = await post('/api/workflow-instances', filter);

  return await response.json();
}
