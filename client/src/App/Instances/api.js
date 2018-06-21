import {post} from 'modules/request';

export async function getCount(filter) {
  const response = await post('/api/workflow-instances/count', filter);

  return (await response.json()).count;
}
