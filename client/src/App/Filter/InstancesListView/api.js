import {post} from 'modules/request';

export async function getData(filter, firstElement) {
  // backend does not support pagination and pageoffset yet :(
  const response = await post('/api/workflow-instances', filter);

  return await response.json();
}
