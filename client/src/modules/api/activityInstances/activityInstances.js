import {post} from 'modules/request';

const URL = '/api/activity-instances';

export async function fetchActivityInstancesTree(workflowInstanceId) {
  const response = await post(URL, {
    workflowInstanceId
  });
  return await response.json();
}
