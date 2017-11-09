import {get, del, put} from 'request';

export async function loadSingleReport(id) {
  const response = await get('/api/report/' + id);

  return await response.json();
}

export async function remove(id) {
  return await del(`/api/report/${id}`);
}

export async function loadProcessDefinitions() {
  const response = await get('/api/process-definition');

  return await response.json();
}

export async function getReportData(query) {
  // let response;
  // if(typeof query !== 'object') {
  //   // evaluate saved report
  //   response = await get(`/api/report/${query}/evaluate`);
  // } else {
  //   // evaluate unsaved report
  //   response = await post('/api/report/evaluate', query);
  // }

  // return await response.json();

  //TODO: use code above once backend implements evaluate query
  return {
    processDefinitionId: 'invoice:2:03e912f2-c49f-11e7-b07e-a0afbd96e1d2',
    filter: null,
    view: {
      operation: 'count',
      entity: 'processInstance'
    },
    groupBy: {
      type: 'startDate', // 'startDate', 'flowNodes'
      unit: 'day' // month, hour, day, year, week, null
    },
    visualization: 'pie',
    result: {
      "assignApprover": 23,
      "approveInvoice": 7,
      "StartEvent_1": 11
    }
  };
}

export async function saveReport(id, data) {
  return await put(`/api/report/${id}`, data);
}
