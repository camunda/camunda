import {get, post} from 'request';

export async function loadProcessDefinitions() {
  const response = await get('/api/process-definition');

  return await response.json();
}

export async function loadProcessDefinitionXml(id) {
  const response = await get('/api/process-definition/xml', {ids: [id]});

  return (await response.json())[id];
}

export async function loadFrequencyData(id, filter) {
  const response = await post('/api/process-definition/heatmap/frequency', {
    processDefinitionId: id,
    filter
  });

  return await response.json();
}

export async function loadCorrelationData(processDefinitionId, filter, gateway, end) {
  const response = await post('/api/process-definition/correlation', {
    processDefinitionId,
    filter,
    gateway,
    end
  });

  return await response.json();
}
