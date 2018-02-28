import {get, post} from 'request';

export async function loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersion) {
  const response = await get('/api/process-definition/xml', {processDefinitionKey, processDefinitionVersion});

  return await response.text();
}

export async function loadFrequencyData(processDefinitionKey, processDefinitionVersion, filter) {
  const response = await post('/api/process-definition/heatmap/frequency', {
    processDefinitionKey,
    processDefinitionVersion,
    filter
  });

  return await response.json();
}

export async function loadCorrelationData(processDefinitionKey, processDefinitionVersion, filter, gateway, end) {
  const response = await post('/api/process-definition/correlation', {
    processDefinitionKey,
    processDefinitionVersion,
    filter,
    gateway,
    end
  });

  return await response.json();
}
