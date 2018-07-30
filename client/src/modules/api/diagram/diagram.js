import {get} from 'modules/request';

export const fetchWorkflowXML = async workflowId => {
  const response = await get(`/api/workflows/${workflowId}/xml`);
  return await response.text();
};
