import {post} from 'request';

export async function getFlowNodeNames(id) {
  const response = await post(`/api/process-definition/${id}/flowNodeNames`, []);

  const json = await response.json();

  return await json.flowNodeNames;
}
