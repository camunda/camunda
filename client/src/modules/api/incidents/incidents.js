import {get} from 'modules/request';

export const fetchIncidentsByWorkflow = async () => {
  const response = await get('/api/incidents/byWorkflow');
  return await response.json();
};
