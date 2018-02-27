import {get, post} from 'request';

export async function loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersion) {
  const response = await get('/api/process-definition/xml', {processDefinitionKey, processDefinitionVersion});

  return await response.text();
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
