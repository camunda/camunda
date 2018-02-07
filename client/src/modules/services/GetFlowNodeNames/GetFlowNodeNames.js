import {post} from 'request';

export default async function getFlowNodeNames(id) {
  const response = await post(`/api/flow-node/${id}/flowNodeNames`, []);

  const json = await response.json();

  return await json.flowNodeNames;
}
