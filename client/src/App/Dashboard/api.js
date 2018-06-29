import {post} from 'modules/request';

const URL = '/api/workflow-instances/count';

export const fetchWorkflowInstancesCount = async payload => {
  const response = await post(URL, payload);
  const resJson = await response.json();
  return resJson.count;
};
