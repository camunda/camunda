import {post} from 'request';

export default async function getFlowNodeNames(processDefinitionKey, processDefinitionVersion) {
  const response = await post(`/api/flow-node/flowNodeNames`, {
    processDefinitionKey,
    processDefinitionVersion,
    nodeIds: []
  });

  const json = await response.json();

  return await json.flowNodeNames;
}
