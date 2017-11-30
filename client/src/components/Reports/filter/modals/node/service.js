import {get} from 'request';

export async function loadDiagramXML(id) {
  const response = await get('/api/process-definition/xml', {ids: [id]});

  return (await response.json())[id];
}
