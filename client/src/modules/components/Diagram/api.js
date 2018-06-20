import {get} from 'modules/request';

export const workflowXML = async workflowId => {
  const response = await get(`/api/workflows/${workflowId}/xml`);
  return await response.text();
};
