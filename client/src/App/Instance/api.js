import {get} from 'modules/request';

export const workflowInstance = async id => {
  const response = await get(`/api/workflow-instances/${id}`);
  return await response.json();
};
