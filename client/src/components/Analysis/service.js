import {get, post} from 'request';

export async function loadProcessDefinitionXml(processDefinitionKey, processDefinitionVersion) {
  const response = await get('api/process-definition/xml', {
    processDefinitionKey,
    processDefinitionVersion
  });

  return await response.text();
}

export async function loadFrequencyData(processDefinitionKey, processDefinitionVersion, filter) {
  const response = await post(
    'api/report/evaluate/single',
    createFlowNodeFrequencyReport(processDefinitionKey, processDefinitionVersion, filter)
  );

  return await response.json();
}

function createFlowNodeFrequencyReport(processDefinitionKey, processDefinitionVersion, filter) {
  return {
    processDefinitionKey,
    processDefinitionVersion,
    filter,
    view: {
      operation: 'count',
      entity: 'flowNode',
      property: 'frequency'
    },
    groupBy: {
      type: 'flowNodes',
      unit: null
    },
    visualization: 'heat'
  };
}

export async function loadCorrelationData(
  processDefinitionKey,
  processDefinitionVersion,
  filter,
  gateway,
  end
) {
  const response = await post('api/analysis/correlation', {
    processDefinitionKey,
    processDefinitionVersion,
    filter,
    gateway,
    end
  });

  return await response.json();
}
